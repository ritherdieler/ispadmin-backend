package com.dscorp.wispadmin.wispadmin.controller

import com.dscorp.wispadmin.wispadmin.config.WhatsAppProperties
import com.dscorp.wispadmin.wispadmin.service.WhatsAppWebhookService
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/whatsapp/webhook")
class WhatsAppWebhookController(
    private val whatsAppProperties: WhatsAppProperties,
    private val whatsAppWebhookService: WhatsAppWebhookService
) {

    private val log = LoggerFactory.getLogger(WhatsAppWebhookController::class.java)

    @GetMapping(produces = [MediaType.TEXT_PLAIN_VALUE])
    fun verifyWebhook(
        @RequestParam("hub.mode") mode: String?,
        @RequestParam("hub.verify_token") verifyToken: String?,
        @RequestParam("hub.challenge") challenge: String?
    ): ResponseEntity<String> {
        if (mode == "subscribe" && verifyToken == whatsAppProperties.webhookVerifyToken) {
            log.info("Webhook verificado por Meta.")
            return ResponseEntity.ok(challenge ?: "")
        }
        log.warn("Webhook: verificacion fallida. mode={}, token_match={}", mode, verifyToken == whatsAppProperties.webhookVerifyToken)
        return ResponseEntity.status(403).body("Verificacion fallida.")
    }

    @PostMapping(consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun receiveEvents(
        @RequestBody rawBody: String,
        @RequestHeader("X-Hub-Signature-256", required = false) signature: String?
    ): ResponseEntity<String> {
        if (!whatsAppWebhookService.verifySignature(rawBody.toByteArray(Charsets.UTF_8), signature)) {
            log.warn("Webhook: firma invalida o app secret no configurado.")
            return ResponseEntity.status(403).body("Firma invalida.")
        }

        whatsAppWebhookService.processPayloadAsync(rawBody)
        return ResponseEntity.ok("EVENT_RECEIVED")
    }
}
