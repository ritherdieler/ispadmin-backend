package com.dscorp.wispadmin.observability.service

import com.dscorp.wispadmin.observability.dto.PagedResponse
import com.dscorp.wispadmin.observability.dto.ReplaySummaryDto
import com.dscorp.wispadmin.observability.dto.SessionDetailDto
import com.dscorp.wispadmin.observability.dto.SessionSummaryDto
import com.dscorp.wispadmin.observability.dto.TraceSummaryDto
import com.dscorp.wispadmin.observability.dto.toDto
import com.dscorp.wispadmin.observability.entity.ObsSpan
import com.dscorp.wispadmin.observability.repository.ObsEventRepository
import com.dscorp.wispadmin.observability.repository.ObsReplayRepository
import com.dscorp.wispadmin.observability.repository.ObsSpanRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import java.sql.Timestamp
import java.time.LocalDateTime

@Service
class ObsSessionQueryService(
    private val eventRepository: ObsEventRepository,
    private val spanRepository: ObsSpanRepository,
    private val replayRepository: ObsReplayRepository,
    private val objectMapper: ObjectMapper
) {

    fun listSessions(
        from: LocalDateTime,
        to: LocalDateTime,
        page: Int,
        size: Int
    ): PagedResponse<SessionSummaryDto> {
        val pageable = PageRequest.of(page.coerceAtLeast(0), size.coerceIn(1, 200))
        val result = eventRepository.aggregateRecentSessions(from, to, pageable)
        val rows = result.content
        val sessionIds = rows.mapNotNull { it[0]?.toString() }

        val traceCounts = if (sessionIds.isNotEmpty()) {
            spanRepository.countRootSpansBySession(sessionIds).associate {
                (it[0]?.toString() ?: "") to (it[1] as Number).toLong()
            }
        } else emptyMap()

        val withReplay = if (sessionIds.isNotEmpty()) {
            replayRepository.findSessionIdsWithReplay(sessionIds).toSet()
        } else emptySet()

        val content = rows.map { row ->
            val sessionId = row[0]?.toString()
            val lastEvent = sessionId?.let { eventRepository.findFirstBySessionIdOrderByCreatedAtDesc(it) }
            SessionSummaryDto(
                sessionId = sessionId,
                platform = row[1]?.toString(),
                eventCount = (row[2] as Number).toLong(),
                traceCount = traceCounts[sessionId] ?: 0L,
                hasReplay = sessionId != null && sessionId in withReplay,
                firstSeen = toLocalDateTime(row[4]),
                lastSeen = toLocalDateTime(row[3]),
                user = lastEvent?.let { parseJson(it.userJson) }
            )
        }

        return PagedResponse(
            content = content,
            page = result.number,
            size = result.size,
            totalElements = result.totalElements,
            totalPages = result.totalPages
        )
    }

    fun getSession(sessionId: String): SessionDetailDto? {
        val events = eventRepository.findBySessionIdOrderByCreatedAtDesc(sessionId)
        val rootSpans = spanRepository.searchRootSpans(
            null, null, null, null, null, null, sessionId, PageRequest.of(0, 200)
        ).content
        val replayEntities = replayRepository.findBySessionIdOrderByCreatedAtAsc(sessionId)

        if (events.isEmpty() && rootSpans.isEmpty() && replayEntities.isEmpty()) return null

        val traces = buildTraceSummaries(rootSpans)
        val formatByReplayId = replayEntities.mapNotNull { r -> r.id?.let { it to r.format } }.toMap()
        val replays = replayEntities.map {
            ReplaySummaryDto(
                id = it.id,
                format = it.format,
                durationMs = it.durationMs,
                sizeBytes = it.sizeBytes,
                createdAt = it.createdAt
            )
        }

        val platform = events.firstOrNull()?.platform ?: rootSpans.firstOrNull()?.platform
        val createdAts = events.mapNotNull { it.createdAt }
        val summary = SessionSummaryDto(
            sessionId = sessionId,
            platform = platform,
            eventCount = events.size.toLong(),
            traceCount = traces.size.toLong(),
            hasReplay = replays.isNotEmpty(),
            firstSeen = createdAts.minOrNull(),
            lastSeen = createdAts.maxOrNull(),
            user = events.firstOrNull()?.let { parseJson(it.userJson) }
        )

        return SessionDetailDto(
            summary = summary,
            events = events.map { it.toDto(::parseJson, it.replayId?.let { id -> formatByReplayId[id] }) },
            traces = traces,
            replays = replays
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

    private fun toLocalDateTime(value: Any?): LocalDateTime? = when (value) {
        is LocalDateTime -> value
        is Timestamp -> value.toLocalDateTime()
        else -> null
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
