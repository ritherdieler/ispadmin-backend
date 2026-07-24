package com.dscorp.wispadmin.wispadmin.controller

import com.dscorp.wispadmin.wispadmin.config.WhatsAppProperties
import com.dscorp.wispadmin.wispadmin.dto.WhatsAppTestSendResponseDto
import com.dscorp.wispadmin.wispadmin.requestbody.WhatsAppTemplateTestMessageRequest
import com.dscorp.wispadmin.wispadmin.requestbody.WhatsAppTestMessageRequest
import com.dscorp.wispadmin.wispadmin.service.WhatsAppService
import com.dscorp.wispadmin.wispadmin.service.WhatsAppWebhookSignatureValidator
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * Webhook de Meta y endpoints de prueba tecnica.
 * Los recordatorios operativos viven en [WhatsAppBackofficeController].
 */
@RestController
@RequestMapping("/whatsapp")
class WhatsAppController(
    private val whatsAppService: WhatsAppService,
    private val whatsAppProperties: WhatsAppProperties,
    private val webhookSignatureValidator: WhatsAppWebhookSignatureValidator,
    private val templateMessageSender: WhatsAppTemplateMessageSender
) {

    private val log = LoggerFactory.getLogger(WhatsAppController::class.java)

    @GetMapping("/webhook")
    fun verifyWebhook(
        @RequestParam("hub.mode") mode: String?,
        @RequestParam("hub.verify_token") verifyToken: String?,
        @RequestParam("hub.challenge") challenge: String?
    ): ResponseEntity<String> {
        if (
            mode == "subscribe" &&
            verifyToken == whatsAppProperties.webhookVerifyToken &&
            !challenge.isNullOrBlank()
        ) {
            log.info("WhatsApp webhook verification succeeded")
            return ResponseEntity.ok(challenge)
        }

        log.warn("WhatsApp webhook verification failed: invalid verify token or mode")
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Invalid verify token")
    }

    @PostMapping("/webhook")
    fun receiveWebhook(
        @RequestBody rawBody: String,
        @RequestHeader(value = "X-Hub-Signature-256", required = false) signature: String?
    ): ResponseEntity<String> {
        if (!webhookSignatureValidator.isValid(rawBody, signature)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Invalid signature")
        }

        log.info("WhatsApp webhook received: {}", rawBody.take(500))
        return ResponseEntity.ok("EVENT_RECEIVED")
    }

    @PostMapping("/test-message")
    fun sendTestMessage(
        @RequestBody request: WhatsAppTestMessageRequest
    ): ResponseEntity<String> {
        return try {
            whatsAppService.sendTextMessage(
                phoneNumber = request.phoneNumber,
                message = request.message
            )
            ResponseEntity.ok("Mensaje enviado correctamente por WhatsApp.")
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body("No se pudo enviar el mensaje por WhatsApp: ${e.message}")
        } catch (e: Exception) {
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("No se pudo enviar el mensaje por WhatsApp: ${e.message}")
        }
    }

    @PostMapping("/test-template-message")
    fun sendTestTemplateMessage(
        @RequestBody request: WhatsAppTemplateTestMessageRequest
    ): ResponseEntity<WhatsAppTestSendResponseDto> {
        return templateMessageSender.sendPaymentReminderTemplate(request)
    }
}
