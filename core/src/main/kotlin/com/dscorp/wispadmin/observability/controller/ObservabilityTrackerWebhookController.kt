package com.dscorp.wispadmin.observability.controller

import com.dscorp.wispadmin.observability.config.ObservabilityProperties
import com.dscorp.wispadmin.observability.service.IssueTrackerRegistry
import com.dscorp.wispadmin.observability.service.ObsTrackerWebhookService
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/observability/tracker")
class ObservabilityTrackerWebhookController(
    private val registry: IssueTrackerRegistry,
    private val webhookService: ObsTrackerWebhookService,
    private val properties: ObservabilityProperties
) {

    private val log = LoggerFactory.getLogger(this::class.java)

    @PostMapping("/{provider}/webhook")
    fun webhook(
        @PathVariable provider: String,
        @RequestHeader(value = "X-Obs-Tracker-Secret", required = false) secret: String?,
        @RequestBody(required = false) rawBody: String?
    ): ResponseEntity<Any> {
        val expected = properties.tracker.webhook.secret
        if (expected.isBlank() || secret != expected) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(mapOf("error" to "unauthorized"))
        }

        val adapter = registry.byProvider(provider)
            ?: return ResponseEntity.badRequest().body(mapOf("error" to "unknown_provider", "provider" to provider))

        val event = adapter.parseWebhook(emptyMap(), rawBody ?: "")
            ?: return ResponseEntity.ok(mapOf("applied" to false, "ignored" to true))

        val applied = webhookService.apply(event)
        log.info("Webhook {} recibido: type={} key={} applied={}", provider, event.type, event.ticketKey, applied)
        return ResponseEntity.ok(
            mapOf(
                "applied" to applied,
                "type" to event.type.name,
                "key" to event.ticketKey
            )
        )
    }
}
