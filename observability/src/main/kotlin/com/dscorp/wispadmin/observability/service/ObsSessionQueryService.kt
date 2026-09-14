package com.dscorp.wispadmin.observability.service

import com.dscorp.wispadmin.observability.dto.PagedResponse
import com.dscorp.wispadmin.observability.dto.ReplaySummaryDto
import com.dscorp.wispadmin.observability.dto.SessionDetailDto
import com.dscorp.wispadmin.observability.dto.SessionSummaryDto
import com.dscorp.wispadmin.observability.dto.TraceSummaryDto
import com.dscorp.wispadmin.observability.dto.toDto
import com.dscorp.wispadmin.observability.entity.ObsEvent
import com.dscorp.wispadmin.observability.entity.ObsSpan
import com.dscorp.wispadmin.observability.repository.ObsEventRepository
import com.dscorp.wispadmin.observability.repository.ObsReplayRepository
import com.dscorp.wispadmin.observability.repository.ObsSpanRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

@Service
class ObsSessionQueryService(
    private val eventRepository: ObsEventRepository,
    private val spanRepository: ObsSpanRepository,
    private val replayRepository: ObsReplayRepository,
    private val objectMapper: ObjectMapper,
    private val zone: ZoneId = ZoneId.systemDefault()
) {

    fun listSessions(
        from: LocalDateTime,
        to: LocalDateTime,
        release: String?,
        page: Int,
        size: Int
    ): PagedResponse<SessionSummaryDto> {
        val normalizedRelease = release?.takeIf { it.isNotBlank() }
        val pageable = PageRequest.of(0, MAX_SESSION_ROWS)
        val eventRows = eventRepository.aggregateRecentSessions(from, to, normalizedRelease, pageable).content
        val fromMs = from.atZone(zone).toInstant().toEpochMilli()
        val toMs = to.atZone(zone).toInstant().toEpochMilli()
        val spanRows = spanRepository.aggregateRecentSessions(fromMs, toMs, normalizedRelease)

        val merged = mergeSessionAggregates(eventRows, spanRows)
        val safePage = page.coerceAtLeast(0)
        val safeSize = size.coerceIn(1, 200)
        val totalElements = merged.size.toLong()
        val totalPages = if (totalElements == 0L) 0 else ((totalElements + safeSize - 1) / safeSize).toInt()
        val pageContent = merged
            .drop(safePage * safeSize)
            .take(safeSize)

        val sessionIds = pageContent.mapNotNull { it.sessionId }

        val traceCounts = if (sessionIds.isNotEmpty()) {
            spanRepository.countRootSpansBySession(sessionIds).associate {
                (it[0]?.toString() ?: "") to (it[1] as Number).toLong()
            }
        } else emptyMap()

        val withReplay = if (sessionIds.isNotEmpty()) {
            replayRepository.findSessionIdsWithReplay(sessionIds).toSet()
        } else emptySet()

        val content = pageContent.map { agg ->
            val sessionId = agg.sessionId
            val lastEvent = sessionId?.let { eventRepository.findFirstBySessionIdOrderByCreatedAtDesc(it) }
            val user = when {
                sessionId == null -> null
                else -> parseJson(lastEvent?.userJson)
                    ?: resolveSessionUser(eventRepository.findBySessionIdOrderByCreatedAtDesc(sessionId))
            }
            SessionSummaryDto(
                sessionId = sessionId,
                platform = agg.platform,
                eventCount = agg.eventCount,
                traceCount = traceCounts[sessionId] ?: agg.rootSpanCount,
                hasReplay = sessionId != null && sessionId in withReplay,
                firstSeen = agg.firstSeen,
                lastSeen = agg.lastSeen,
                user = user
            )
        }

        return PagedResponse(
            content = content,
            page = safePage,
            size = safeSize,
            totalElements = totalElements,
            totalPages = totalPages
        )
    }

    fun getSession(
        sessionId: String,
        feature: String? = null,
        action: String? = null,
        workflowId: String? = null
    ): SessionDetailDto? {
        val normalizedFeature = feature?.takeIf { it.isNotBlank() }
        val normalizedAction = action?.takeIf { it.isNotBlank() }
        val normalizedWorkflowId = workflowId?.takeIf { it.isNotBlank() }
        val allEvents = eventRepository.findBySessionIdOrderByCreatedAtDesc(sessionId)
        val events = if (normalizedFeature != null || normalizedAction != null || normalizedWorkflowId != null) {
            eventRepository.findSessionEventsFiltered(
                sessionId,
                normalizedFeature,
                normalizedAction,
                normalizedWorkflowId
            )
        } else {
            allEvents
        }
        val rootSpans = spanRepository.searchRootSpans(
            null, null, null, null, null, null, sessionId, null, PageRequest.of(0, 200)
        ).content
        val replayEntities = replayRepository.findBySessionIdOrderByCreatedAtAsc(sessionId)

        if (allEvents.isEmpty() && rootSpans.isEmpty() && replayEntities.isEmpty()) return null

        val traces = buildTraceSummaries(rootSpans)
        val formatByReplayId = replayEntities.mapNotNull { r -> r.id?.let { it to r.format } }.toMap()
        val replays = replayEntities.map {
            ReplaySummaryDto(
                id = it.id,
                format = it.format,
                durationMs = it.durationMs,
                sizeBytes = it.sizeBytes,
                createdAt = it.createdAt,
                workflowId = it.workflowId
            )
        }

        val platform = allEvents.firstOrNull()?.platform ?: rootSpans.firstOrNull()?.platform
        val createdAts = allEvents.mapNotNull { it.createdAt }
        val spanTimes = rootSpans.mapNotNull { span ->
            span.startEpochMs?.let { epochMsToLocalDateTime(it) }
        }
        val allTimes = createdAts + spanTimes
        val summary = SessionSummaryDto(
            sessionId = sessionId,
            platform = platform,
            eventCount = allEvents.size.toLong(),
            traceCount = traces.size.toLong(),
            hasReplay = replays.isNotEmpty(),
            firstSeen = allTimes.minOrNull(),
            lastSeen = allTimes.maxOrNull(),
            user = resolveSessionUser(allEvents)
        )

        return SessionDetailDto(
            summary = summary,
            events = events.map { it.toDto(::parseJson, it.replayId?.let { id -> formatByReplayId[id] }) },
            traces = traces,
            replays = replays,
            workflows = ObsWorkflowAggregation.fromEvents(allEvents)
        )
    }

    private fun mergeSessionAggregates(
        eventRows: List<Array<Any>>,
        spanRows: List<Array<Any>>
    ): List<SessionAggregate> {
        val byId = LinkedHashMap<String, SessionAggregate>()
        for (row in eventRows) {
            val sessionId = row[0]?.toString() ?: continue
            byId[sessionId] = SessionAggregate(
                sessionId = sessionId,
                platform = row[1]?.toString(),
                eventCount = (row[2] as Number).toLong(),
                lastSeen = toLocalDateTime(row[3]),
                firstSeen = toLocalDateTime(row[4]),
                rootSpanCount = 0L
            )
        }
        for (row in spanRows) {
            val sessionId = row[0]?.toString() ?: continue
            val rootSpanCount = (row[2] as Number).toLong()
            val lastSeen = epochMsToLocalDateTime(row[3] as Number)
            val firstSeen = epochMsToLocalDateTime(row[4] as Number)
            val existing = byId[sessionId]
            if (existing == null) {
                byId[sessionId] = SessionAggregate(
                    sessionId = sessionId,
                    platform = row[1]?.toString(),
                    eventCount = 0L,
                    lastSeen = lastSeen,
                    firstSeen = firstSeen,
                    rootSpanCount = rootSpanCount
                )
            } else {
                byId[sessionId] = existing.copy(
                    platform = existing.platform ?: row[1]?.toString(),
                    lastSeen = maxOfLocalDateTime(existing.lastSeen, lastSeen),
                    firstSeen = minOfLocalDateTime(existing.firstSeen, firstSeen),
                    rootSpanCount = maxOf(existing.rootSpanCount, rootSpanCount)
                )
            }
        }
        return byId.values.sortedByDescending { it.lastSeen ?: LocalDateTime.MIN }
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
                hasError = errorCount > 0 || root.status == "ERROR",
                release = root.release
            )
        }
    }

    private fun resolveSessionUser(eventsNewestFirst: List<ObsEvent>): Any? =
        eventsNewestFirst.firstNotNullOfOrNull { parseJson(it.userJson) }

    private fun toLocalDateTime(value: Any?): LocalDateTime? = when (value) {
        is LocalDateTime -> value
        is Timestamp -> value.toLocalDateTime()
        else -> null
    }

    private fun epochMsToLocalDateTime(value: Number): LocalDateTime? =
        LocalDateTime.ofInstant(Instant.ofEpochMilli(value.toLong()), zone)

    private fun maxOfLocalDateTime(a: LocalDateTime?, b: LocalDateTime?): LocalDateTime? = when {
        a == null -> b
        b == null -> a
        else -> if (a.isAfter(b)) a else b
    }

    private fun minOfLocalDateTime(a: LocalDateTime?, b: LocalDateTime?): LocalDateTime? = when {
        a == null -> b
        b == null -> a
        else -> if (a.isBefore(b)) a else b
    }

    private fun parseJson(raw: String?): Any? {
        if (raw.isNullOrBlank()) return null
        return try {
            objectMapper.readValue(raw, Any::class.java)
        } catch (e: Exception) {
            raw
        }
    }

    private data class SessionAggregate(
        val sessionId: String,
        val platform: String?,
        val eventCount: Long,
        val lastSeen: LocalDateTime?,
        val firstSeen: LocalDateTime?,
        val rootSpanCount: Long
    )

    companion object {
        private const val MAX_SESSION_ROWS = 5000
    }
}
