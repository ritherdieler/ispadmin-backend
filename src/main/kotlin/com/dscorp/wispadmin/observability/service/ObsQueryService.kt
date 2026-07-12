package com.dscorp.wispadmin.observability.service

import com.dscorp.wispadmin.observability.dto.EventDto
import com.dscorp.wispadmin.observability.dto.IssueSummaryDto
import com.dscorp.wispadmin.observability.dto.OverviewStatsDto
import com.dscorp.wispadmin.observability.dto.PagedResponse
import com.dscorp.wispadmin.observability.dto.TimeSeriesPointDto
import com.dscorp.wispadmin.observability.dto.TraceSummaryDto
import com.dscorp.wispadmin.observability.dto.toDto
import com.dscorp.wispadmin.observability.dto.toSummaryDto
import com.dscorp.wispadmin.observability.entity.ObsEvent
import com.dscorp.wispadmin.observability.entity.ObsIssue
import com.dscorp.wispadmin.observability.entity.ObsIssueStatus
import com.dscorp.wispadmin.observability.entity.ObsSpan
import com.dscorp.wispadmin.observability.repository.ObsEventRepository
import com.dscorp.wispadmin.observability.repository.ObsIssueRepository
import com.dscorp.wispadmin.observability.repository.ObsReplayRepository
import com.dscorp.wispadmin.observability.repository.ObsSpanRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class ObsQueryService(
    private val issueRepository: ObsIssueRepository,
    private val eventRepository: ObsEventRepository,
    private val spanRepository: ObsSpanRepository,
    private val replayRepository: ObsReplayRepository,
    private val symbolicationService: ObsSymbolicationService,
    private val objectMapper: ObjectMapper
) {

    fun searchIssues(
        platform: String?,
        severity: String?,
        status: ObsIssueStatus?,
        environment: String?,
        from: LocalDateTime?,
        to: LocalDateTime?,
        text: String?,
        page: Int,
        size: Int
    ): PagedResponse<IssueSummaryDto> {
        val pageable = PageRequest.of(
            page.coerceAtLeast(0),
            size.coerceIn(1, 200),
            Sort.by(Sort.Direction.DESC, "lastSeen")
        )
        val result = issueRepository.search(
            platform?.takeIf { it.isNotBlank() },
            severity?.takeIf { it.isNotBlank() },
            status,
            environment?.takeIf { it.isNotBlank() },
            from,
            to,
            text?.takeIf { it.isNotBlank() },
            pageable
        )
        return PagedResponse(
            content = result.content.map { it.toSummaryDto() },
            page = result.number,
            size = result.size,
            totalElements = result.totalElements,
            totalPages = result.totalPages
        )
    }

    fun getIssue(id: Long): IssueSummaryDto? =
        issueRepository.findById(id).map { it.toSummaryDto() }.orElse(null)

    fun getIssueEntity(id: Long): ObsIssue? =
        issueRepository.findById(id).orElse(null)

    fun getOccurrences(issueId: Long, page: Int, size: Int): PagedResponse<EventDto> {
        val pageable = PageRequest.of(page.coerceAtLeast(0), size.coerceIn(1, 200))
        val result = eventRepository.findByIssueIdOrderByCreatedAtDesc(issueId, pageable)
        result.content.forEach { ensureSymbolicated(it) }
        val formatByReplayId = resolveReplayFormats(result.content.mapNotNull { it.replayId })
        return PagedResponse(
            content = result.content.map { it.toDto(::parseJson, it.replayId?.let { id -> formatByReplayId[id] }) },
            page = result.number,
            size = result.size,
            totalElements = result.totalElements,
            totalPages = result.totalPages
        )
    }

    fun getLatestEvent(issueId: Long): EventDto? {
        val event = eventRepository.findFirstByIssueIdOrderByCreatedAtDesc(issueId) ?: return null
        ensureSymbolicated(event)
        val format = event.replayId?.let { replayRepository.findById(it).orElse(null)?.format }
        return event.toDto(::parseJson, format)
    }

    private fun ensureSymbolicated(event: ObsEvent) {
        if (!event.stacktraceSymbolicated.isNullOrBlank()) return
        if (event.stacktrace.isNullOrBlank()) return
        val symbolicated = symbolicationService.symbolicate(event) ?: return
        event.stacktraceSymbolicated = symbolicated
        eventRepository.save(event)
    }

    private fun resolveReplayFormats(replayIds: List<Long>): Map<Long, String?> {
        if (replayIds.isEmpty()) return emptyMap()
        return replayRepository.findAllById(replayIds.distinct())
            .mapNotNull { r -> r.id?.let { it to r.format } }
            .toMap()
    }

    fun changeStatus(id: Long, status: ObsIssueStatus): IssueSummaryDto? {
        val issue = issueRepository.findById(id).orElse(null) ?: return null
        issue.status = status
        return issueRepository.save(issue).toSummaryDto()
    }

    fun overview(): OverviewStatsDto {
        val now = LocalDateTime.now()
        val byPlatform = issueRepository.countGroupedByPlatform().associate {
            (it[0]?.toString() ?: "unknown") to (it[1] as Number).toLong()
        }
        val bySeverity = issueRepository.countOpenGroupedBySeverity().associate {
            (it[0]?.toString() ?: "unknown") to (it[1] as Number).toLong()
        }
        val topIssues = issueRepository.findTopOpenIssues(PageRequest.of(0, 10)).map { it.toSummaryDto() }

        val nowMs = System.currentTimeMillis()
        val lastHourMs = nowMs - 3_600_000L
        val rootAgg = spanRepository.aggregateRootSpansSince(lastHourMs).firstOrNull()
        val tracesLastHour = (rootAgg?.get(0) as? Number)?.toLong() ?: 0L
        val errorTraces = (rootAgg?.get(1) as? Number)?.toLong() ?: 0L
        val errorTraceRate = if (tracesLastHour > 0) errorTraces.toDouble() / tracesLastHour else 0.0
        val p95TraceDurationMs = percentile(spanRepository.rootSpanDurationsSince(lastHourMs), 0.95)
        val slowRoots = spanRepository.findSlowestRootSpansSince(nowMs - 86_400_000L, PageRequest.of(0, 5))
        val slowTraces = buildTraceSummaries(slowRoots)

        return OverviewStatsDto(
            openIssues = issueRepository.countByStatus(ObsIssueStatus.OPEN),
            resolvedIssues = issueRepository.countByStatus(ObsIssueStatus.RESOLVED),
            ignoredIssues = issueRepository.countByStatus(ObsIssueStatus.IGNORED),
            eventsLast24h = eventRepository.countSince(now.minusHours(24)),
            eventsLastHour = eventRepository.countSince(now.minusHours(1)),
            issuesByPlatform = byPlatform,
            openIssuesBySeverity = bySeverity,
            topIssues = topIssues,
            tracesLastHour = tracesLastHour,
            errorTraceRate = errorTraceRate,
            p95TraceDurationMs = p95TraceDurationMs,
            slowTraces = slowTraces
        )
    }

    private fun buildTraceSummaries(rootSpans: List<ObsSpan>): List<TraceSummaryDto> {
        val traceIds = rootSpans.mapNotNull { it.traceId }
        val aggregates = if (traceIds.isNotEmpty()) {
            spanRepository.aggregateByTraceIds(traceIds).associate {
                (it[0]?.toString() ?: "") to Pair((it[1] as Number).toLong(), (it[2] as Number).toLong())
            }
        } else emptyMap()

        return rootSpans.map { root ->
            val agg = aggregates[root.traceId]
            val spanCount = agg?.first ?: 1L
            val errorCount = agg?.second ?: 0L
            TraceSummaryDto(
                traceId = root.traceId,
                rootName = root.name,
                rootSpanId = root.spanId,
                platform = root.platform,
                sessionId = root.sessionId,
                httpMethod = root.httpMethod,
                httpRoute = root.httpRoute,
                httpStatus = root.httpStatus,
                status = root.status,
                startEpochMs = root.startEpochMs,
                durationMs = root.durationMs,
                spanCount = spanCount,
                hasError = errorCount > 0 || root.status == "ERROR"
            )
        }
    }

    private fun percentile(sortedAsc: List<Long>, p: Double): Long {
        if (sortedAsc.isEmpty()) return 0L
        val index = (Math.ceil(p * sortedAsc.size).toInt()).coerceIn(1, sortedAsc.size) - 1
        return sortedAsc[index]
    }

    fun eventsTimeSeries(from: LocalDateTime, to: LocalDateTime): List<TimeSeriesPointDto> {
        return eventRepository.timeSeriesByPlatform(from, to).map {
            TimeSeriesPointDto(
                bucket = it[0]?.toString() ?: "",
                platform = it[1]?.toString(),
                count = (it[2] as Number).toLong()
            )
        }
    }

    private fun parseJson(raw: String?): Any? {
        if (raw.isNullOrBlank()) return null
        return try {
            objectMapper.readValue(raw, Any::class.java)
        } catch (e: Exception) {
            raw
        }
    }
}
