package com.dscorp.wispadmin.observability.controller

import com.dscorp.wispadmin.observability.config.ObservabilityApiKeyFilter
import com.dscorp.wispadmin.observability.config.ObservabilityProperties
import com.dscorp.wispadmin.observability.dto.RumIngestBatchRequest
import com.dscorp.wispadmin.observability.dto.RumIngestResponse
import com.dscorp.wispadmin.observability.service.ObsIngestionService
import com.dscorp.wispadmin.observability.service.ObsRumCollector
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import javax.servlet.http.HttpServletRequest

@RestController
@RequestMapping("/observability")
class ObservabilityRumController(
    private val ingestionService: ObsIngestionService,
    private val rumCollector: ObsRumCollector,
    private val properties: ObservabilityProperties
) {

    @PostMapping("/rum")
    fun ingest(
        @RequestBody batch: RumIngestBatchRequest,
        request: HttpServletRequest
    ): ResponseEntity<RumIngestResponse> {
        if (!properties.enabled || !properties.rum.enabled) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build()
        }

        val platformFromKey = request.getAttribute(ObservabilityApiKeyFilter.PLATFORM_ATTRIBUTE) as? String
        val rateKey = platformFromKey ?: request.remoteAddr ?: "unknown"
        if (!ingestionService.allowRequest(rateKey)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build()
        }

        val platform = platformFromKey ?: "unknown"
        val allowedMetrics = properties.rum.allowedMetrics.map { it.uppercase() }.toSet()
        val vitals = batch.vitals.take(properties.maxEventsPerBatch)
        var accepted = 0
        var rejected = 0

        for (raw in vitals) {
            val metricName = raw.metricName?.trim()?.uppercase()
            val value = raw.value
            val page = raw.page?.takeIf { it.isNotBlank() }
            if (metricName == null || metricName !in allowedMetrics || value == null || page == null) {
                rejected++
                continue
            }
            try {
                rumCollector.record(page, platform, metricName, value, raw.rating, raw.release?.takeIf { it.isNotBlank() })
                accepted++
            } catch (e: Exception) {
                rejected++
            }
        }

        val rejectedByLimit = batch.vitals.size - vitals.size
        return ResponseEntity.status(HttpStatus.ACCEPTED)
            .body(RumIngestResponse(accepted, rejected + rejectedByLimit))
    }
}
