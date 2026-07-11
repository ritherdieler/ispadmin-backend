package com.dscorp.wispadmin.wispadmin.controller

import com.dscorp.wispadmin.wispadmin.requestbody.WhatsAppTestMessageRequest
import com.dscorp.wispadmin.wispadmin.service.WhatsAppService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import com.dscorp.wispadmin.wispadmin.repository.WhatsAppMessageLogRepository
import com.dscorp.wispadmin.wispadmin.repository.WhatsAppInboundMessageRepository
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import com.dscorp.wispadmin.wispadmin.dto.toDto
import com.dscorp.wispadmin.wispadmin.config.WhatsAppProperties
import com.dscorp.wispadmin.wispadmin.requestbody.WhatsAppTemplateTestMessageRequest

@RestController
@RequestMapping("/whatsapp")
class WhatsAppController(
    private val whatsAppService: WhatsAppService,
    private val whatsAppMessageLogRepository: WhatsAppMessageLogRepository,
    private val whatsAppInboundMessageRepository: WhatsAppInboundMessageRepository,
    private val whatsAppProperties: WhatsAppProperties
) {
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
            val wamid = whatsAppService.sendTextMessage(
                phoneNumber = request.phoneNumber,
                message = request.message
            )
            val detail = if (wamid != null) " (id: $wamid)" else ""
            ResponseEntity.ok("Mensaje enviado correctamente por WhatsApp.$detail")
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
            val wamid = whatsAppService.sendTemplateMessage(
                phoneNumber = request.phoneNumber,
                templateName = whatsAppProperties.paymentReminderTemplateName,
                languageCode = whatsAppProperties.paymentReminderTemplateLanguage,
                parameters = listOf(
                    request.clientName,
                    request.amount,
                    request.billingPeriod
                )
            )
            val detail = if (wamid != null) " (id: $wamid)" else ""
            ResponseEntity.ok("Plantilla enviada correctamente por WhatsApp.$detail")
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

    @GetMapping("/inbound-messages")
    fun getRecentInboundMessages(): ResponseEntity<Any> {
        return ResponseEntity.ok(
            whatsAppInboundMessageRepository.findTop50ByOrderByCreatedAtDesc().map { it.toDto() }
        )
    }

    @GetMapping("/inbound-messages/subscription/{subscriptionId}")
    fun getInboundMessagesBySubscription(
        @PathVariable subscriptionId: Int
    ): ResponseEntity<Any> {
        return ResponseEntity.ok(
            whatsAppInboundMessageRepository.findBySubscriptionIdOrderByCreatedAtDesc(subscriptionId).map { it.toDto() }
        )
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
