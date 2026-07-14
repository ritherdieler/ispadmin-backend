package com.dscorp.wispadmin.observability.service

import com.dscorp.wispadmin.observability.dto.PagedResponse
import com.dscorp.wispadmin.observability.dto.SpanDto
import com.dscorp.wispadmin.observability.dto.TraceDetailDto
import com.dscorp.wispadmin.observability.dto.TraceSummaryDto
import com.dscorp.wispadmin.observability.dto.toDto
import com.dscorp.wispadmin.observability.entity.ObsSpan
import com.dscorp.wispadmin.observability.repository.ObsEventRepository
import com.dscorp.wispadmin.observability.repository.ObsReplayRepository
import com.dscorp.wispadmin.observability.repository.ObsSpanRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service

@Service
class ObsTraceQueryService(
    private val spanRepository: ObsSpanRepository,
    private val eventRepository: ObsEventRepository,
    private val replayRepository: ObsReplayRepository,
    private val objectMapper: ObjectMapper
) {

    fun searchTraces(
        from: Long?,
        to: Long?,
        route: String?,
        minDurationMs: Long?,
        status: String?,
        platform: String?,
        sessionId: String?,
        release: String?,
        page: Int,
        size: Int
    ): PagedResponse<TraceSummaryDto> {
        val pageable = PageRequest.of(page.coerceAtLeast(0), size.coerceIn(1, 200))
        val result = spanRepository.searchRootSpans(
            from,
            to,
            route?.takeIf { it.isNotBlank() },
            minDurationMs,
            status?.takeIf { it.isNotBlank() },
            platform?.takeIf { it.isNotBlank() },
            sessionId?.takeIf { it.isNotBlank() },
            release?.takeIf { it.isNotBlank() },
            pageable
        )

        val traceIds = result.content.mapNotNull { it.traceId }
        val aggregates = if (traceIds.isNotEmpty()) {
            spanRepository.aggregateByTraceIds(traceIds).associate {
                (it[0]?.toString() ?: "") to Pair((it[1] as Number).toLong(), (it[2] as Number).toLong())
            }
        } else emptyMap()

        val content = result.content.map { root ->
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

        return PagedResponse(
            content = content,
            page = result.number,
            size = result.size,
            totalElements = result.totalElements,
            totalPages = result.totalPages
        )
    }

    fun getTrace(traceId: String): TraceDetailDto? {
        val spans = spanRepository.findByTraceIdOrderByStartEpochMsAsc(traceId)
        if (spans.isEmpty()) return null
        val events = eventRepository.findByCorrelationIdOrderByCreatedAtDesc(traceId)
        val replayIds = events.mapNotNull { it.replayId }.distinct()
        val formatByReplayId = if (replayIds.isEmpty()) emptyMap() else
            replayRepository.findAllById(replayIds).mapNotNull { r -> r.id?.let { it to r.format } }.toMap()
        return TraceDetailDto(
            traceId = traceId,
            spans = spans.map { it.toSpanDto() },
            events = events.map { it.toDto(::parseJson, it.replayId?.let { id -> formatByReplayId[id] }) }
        )
    }

    private fun ObsSpan.toSpanDto() = SpanDto(
        id = id,
        traceId = traceId,
        spanId = spanId,
        parentSpanId = parentSpanId,
        name = name,
        kind = kind,
        platform = platform,
        sessionId = sessionId,
        startEpochMs = startEpochMs,
        durationMs = durationMs,
        status = status,
        httpMethod = httpMethod,
        httpRoute = httpRoute,
        httpStatus = httpStatus,
        dbStatement = dbStatement,
        tags = parseJson(tagsJson),
        environment = environment,
        release = release
    )

    private fun parseJson(raw: String?): Any? {
        if (raw.isNullOrBlank()) return null
        return try {
            objectMapper.readValue(raw, Any::class.java)
        } catch (e: Exception) {
            raw
        }
    }
}
