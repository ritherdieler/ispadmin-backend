package com.dscorp.wispadmin.observability.controller

import com.dscorp.wispadmin.observability.config.ObservabilityApiKeyFilter
import com.dscorp.wispadmin.observability.config.ObservabilityProperties
import com.dscorp.wispadmin.observability.dto.SpanIngestRequest
import com.dscorp.wispadmin.observability.dto.SpanIngestResponse
import com.dscorp.wispadmin.observability.entity.ObsSpan
import com.dscorp.wispadmin.observability.service.ObsSpanCollector
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import javax.servlet.http.HttpServletRequest

@RestController
@RequestMapping("/observability")
class ObservabilitySpanController(
    private val spanCollector: ObsSpanCollector,
    private val properties: ObservabilityProperties
) {

    @PostMapping("/spans")
    fun ingest(
        @RequestBody batch: List<SpanIngestRequest>,
        request: HttpServletRequest
    ): ResponseEntity<SpanIngestResponse> {
        if (!properties.enabled || !properties.tracing.enabled) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build()
        }

        val platformFromKey = request.getAttribute(ObservabilityApiKeyFilter.PLATFORM_ATTRIBUTE) as? String

        var accepted = 0
        var rejected = 0
        for (raw in batch) {
            val span = toEntity(raw, platformFromKey)
            if (span == null) {
                rejected++
                continue
            }
            spanCollector.enqueue(span)
            accepted++
        }

        return ResponseEntity.status(HttpStatus.ACCEPTED)
            .body(SpanIngestResponse(accepted, rejected))
    }

    private fun toEntity(raw: SpanIngestRequest, platformFromKey: String?): ObsSpan? {
        val traceId = raw.traceId?.takeIf { it.isNotBlank() } ?: return null
        val spanId = raw.spanId?.takeIf { it.isNotBlank() } ?: return null
        return ObsSpan(
            traceId = traceId.take(32),
            spanId = spanId.take(16),
            parentSpanId = raw.parentSpanId?.takeIf { it.isNotBlank() }?.take(16),
            name = raw.name?.take(500),
            kind = raw.kind?.takeIf { it.isNotBlank() }?.take(20) ?: "INTERNAL",
            platform = raw.platform?.takeIf { it.isNotBlank() } ?: platformFromKey ?: "unknown",
            sessionId = raw.sessionId?.take(100),
            startEpochMs = raw.startEpochMs,
            durationMs = raw.durationMs,
            status = raw.status?.takeIf { it.isNotBlank() }?.take(10) ?: "OK",
            httpMethod = raw.httpMethod?.take(12),
            httpRoute = raw.httpRoute?.take(500),
            httpStatus = raw.httpStatus,
            dbStatement = raw.dbStatement?.take(2000),
            tagsJson = raw.tagsJson,
            environment = raw.environment?.take(60),
            release = raw.release?.take(120)
        )
    }
}
