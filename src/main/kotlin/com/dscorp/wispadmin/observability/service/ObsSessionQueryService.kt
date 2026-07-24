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
        release: String?,
        page: Int,
        size: Int
    ): PagedResponse<SessionSummaryDto> {
        val pageable = PageRequest.of(page.coerceAtLeast(0), size.coerceIn(1, 200))
        val result = eventRepository.aggregateRecentSessions(from, to, release?.takeIf { it.isNotBlank() }, pageable)
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
            val user = when {
                sessionId == null -> null
                else -> parseJson(lastEvent?.userJson)
                    ?: resolveSessionUser(eventRepository.findBySessionIdOrderByCreatedAtDesc(sessionId))
            }
            SessionSummaryDto(
                sessionId = sessionId,
                platform = row[1]?.toString(),
                eventCount = (row[2] as Number).toLong(),
                traceCount = traceCounts[sessionId] ?: 0L,
                hasReplay = sessionId != null && sessionId in withReplay,
                firstSeen = toLocalDateTime(row[4]),
                lastSeen = toLocalDateTime(row[3]),
                user = user
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
        val summary = SessionSummaryDto(
            sessionId = sessionId,
            platform = platform,
            eventCount = allEvents.size.toLong(),
            traceCount = traces.size.toLong(),
            hasReplay = replays.isNotEmpty(),
            firstSeen = createdAts.minOrNull(),
            lastSeen = createdAts.maxOrNull(),
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

    private fun parseJson(raw: String?): Any? {
        if (raw.isNullOrBlank()) return null
        return try {
            objectMapper.readValue(raw, Any::class.java)
        } catch (e: Exception) {
            raw
        }
    }
}
