package com.dscorp.wispadmin.wispadmin.controller

import com.dscorp.wispadmin.wispadmin.requestbody.WhatsAppTestMessageRequest
import com.dscorp.wispadmin.wispadmin.service.WhatsAppService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import com.dscorp.wispadmin.wispadmin.repository.WhatsAppMessageLogRepository
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import com.dscorp.wispadmin.wispadmin.dto.toDto
import com.dscorp.wispadmin.wispadmin.config.WhatsAppProperties
import com.dscorp.wispadmin.wispadmin.requestbody.WhatsAppTemplateTestMessageRequest
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.http.HttpStatus

@RestController
@RequestMapping("/whatsapp")
class WhatsAppController(
    private val whatsAppService: WhatsAppService,
    private val whatsAppMessageLogRepository: WhatsAppMessageLogRepository,
    private val whatsAppProperties: WhatsAppProperties
) {

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
            return ResponseEntity.ok(challenge)
        }

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Invalid verify token")
    }

    @PostMapping("/webhook")
    fun receiveWebhook(
        @RequestBody payload: Map<String, Any?>
    ): ResponseEntity<String> {
        println("WhatsApp webhook received: $payload")
        return ResponseEntity.ok("EVENT_RECEIVED")
    }

    @GetMapping("/logs")
    fun getRecentLogs(): ResponseEntity<Any> {
        return ResponseEntity.ok(
            whatsAppMessageLogRepository.findTop50ByOrderByCreatedAtDesc()
                .map { it.toDto() }
        )
    }

    @GetMapping("/logs/payment/{paymentId}")
    fun getLogsByPaymentId(
        @PathVariable paymentId: Int
    ): ResponseEntity<Any> {
        return ResponseEntity.ok(
            whatsAppMessageLogRepository.findByPaymentIdOrderByCreatedAtDesc(paymentId)
                .map { it.toDto() }
        )
    }

    // Endpoint temporal para validar que el backend puede enviar mensajes por WhatsApp Cloud API.
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
            ResponseEntity
                .badRequest()
                .body("No se pudo enviar el mensaje por WhatsApp: ${e.message}")
        } catch (e: Exception) {
            ResponseEntity
                .status(500)
                .body("No se pudo enviar el mensaje por WhatsApp: ${e.message}")
        }
    }

    @PostMapping("/test-template-message")
    fun sendTestTemplateMessage(
        @RequestBody request: WhatsAppTemplateTestMessageRequest
    ): ResponseEntity<String> {
        return try {
            whatsAppService.sendTemplateMessage(
                phoneNumber = request.phoneNumber,
                templateName = whatsAppProperties.paymentReminderTemplateName,
                languageCode = whatsAppProperties.paymentReminderTemplateLanguage,
                parameters = listOf(
                    request.clientName,
                    request.amount,
                    request.billingPeriod
                )
            )

            ResponseEntity.ok("Plantilla enviada correctamente por WhatsApp.")
        } catch (e: IllegalArgumentException) {
            ResponseEntity
                .badRequest()
                .body("No se pudo enviar la plantilla por WhatsApp: ${e.message}")
        } catch (e: Exception) {
            ResponseEntity
                .status(500)
                .body("No se pudo enviar la plantilla por WhatsApp: ${friendlyTemplateErrorMessage(e.message)}")
        }
    }

    private fun friendlyTemplateErrorMessage(errorMessage: String?): String {
        if (errorMessage.isNullOrBlank()) {
            return "No se pudo enviar la plantilla."
        }

        return when {
            errorMessage.contains("132001") ||
                    errorMessage.contains("Template name does not exist", ignoreCase = true) -> {
                "La plantilla de WhatsApp no existe, no esta aprobada o el idioma configurado no coincide."
            }

            else -> errorMessage
        }
    }
}
