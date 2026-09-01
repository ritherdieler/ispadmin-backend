package com.dscorp.wispadmin.observability.controller

import com.dscorp.wispadmin.observability.config.ObservabilityApiKeyFilter
import com.dscorp.wispadmin.observability.config.ObservabilityProperties
import com.dscorp.wispadmin.observability.dto.EventIngestBatchRequest
import com.dscorp.wispadmin.observability.dto.EventIngestRequest
import com.dscorp.wispadmin.observability.dto.EventIngestResponse
import com.dscorp.wispadmin.wispadmin.observability.ReportedEvent
import com.dscorp.wispadmin.observability.service.ObsIngestionService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import javax.servlet.http.HttpServletRequest

@RestController
@RequestMapping("/observability")
class ObservabilityEventController(
    private val ingestionService: ObsIngestionService,
    private val properties: ObservabilityProperties
) {

    @PostMapping("/events")
    fun ingest(
        @RequestBody batch: EventIngestBatchRequest,
        request: HttpServletRequest
    ): ResponseEntity<EventIngestResponse> {
        if (!properties.enabled) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build()
        }

        val platformFromKey = request.getAttribute(ObservabilityApiKeyFilter.PLATFORM_ATTRIBUTE) as? String
        val rateKey = platformFromKey ?: request.remoteAddr ?: "unknown"
        if (!ingestionService.allowRequest(rateKey)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build()
        }

        val events = batch.events.take(properties.maxEventsPerBatch)
        val issueIds = ArrayList<Long>()
        var accepted = 0
        var rejected = 0

        for (raw in events) {
            try {
                val reported = toReportedEvent(raw, platformFromKey)
                val issueId = ingestionService.persistEvent(reported)
                if (issueId != null) issueIds.add(issueId)
                accepted++
            } catch (e: Exception) {
                rejected++
            }
        }

        val rejectedByLimit = batch.events.size - events.size
        return ResponseEntity.status(HttpStatus.ACCEPTED)
            .body(EventIngestResponse(accepted, rejected + rejectedByLimit, issueIds.distinct()))
    }

    private fun toReportedEvent(raw: EventIngestRequest, platformFromKey: String?): ReportedEvent {
        val platform = raw.platform?.takeIf { it.isNotBlank() } ?: platformFromKey ?: "unknown"
        return ReportedEvent(
            eventType = raw.eventType?.takeIf { it.isNotBlank() } ?: "error",
            platform = platform,
            severity = raw.severity?.takeIf { it.isNotBlank() } ?: "error",
            message = raw.message,
            errorType = raw.errorType,
            stacktrace = raw.stacktrace,
            environment = raw.environment,
            release = raw.release,
            correlationId = raw.correlationId,
            sessionId = raw.sessionId,
            url = raw.url,
            httpMethod = raw.httpMethod,
            httpStatus = raw.httpStatus,
            durationMs = raw.durationMs,
            userAgent = raw.userAgent,
            user = raw.user,
            device = raw.device,
            breadcrumbs = raw.breadcrumbs,
            tags = raw.tags,
            context = raw.context,
            replayId = raw.replayId,
            timestamp = raw.timestamp
        )
    }
}
