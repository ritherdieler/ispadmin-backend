package com.dscorp.wispadmin.wispadmin.controller

import com.dscorp.wispadmin.wispadmin.requestbody.WhatsAppTestMessageRequest
import com.dscorp.wispadmin.wispadmin.service.WhatsAppService
import com.dscorp.wispadmin.wispadmin.service.WhatsAppWebhookSignatureValidator
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import com.dscorp.wispadmin.wispadmin.repository.WhatsAppMessageLogRepository
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import com.dscorp.wispadmin.wispadmin.dto.WhatsAppTestSendResponseDto
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
    private val whatsAppProperties: WhatsAppProperties,
    private val webhookSignatureValidator: WhatsAppWebhookSignatureValidator
) {

    private val log = LoggerFactory.getLogger(this::class.java)

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
    ): ResponseEntity<WhatsAppTestSendResponseDto> {
        return try {
            val result = whatsAppService.sendTemplateMessageWithMetaResponse(
                phoneNumber = request.phoneNumber,
                templateName = whatsAppProperties.paymentReminderTemplateName,
                languageCode = whatsAppProperties.paymentReminderTemplateLanguage,
                parameters = listOf(
                    request.clientName,
                    request.amount,
                    request.billingPeriod
                )
            )

            ResponseEntity.ok(
                WhatsAppTestSendResponseDto(
                    success = true,
                    message = "Plantilla enviada correctamente por WhatsApp.",
                    recipient = result.recipient,
                    senderPhoneNumberId = result.senderPhoneNumberId,
                    metaResponse = result.metaResponse,
                    deliveryHint = "Meta acepto el envio. Revisa WhatsApp del numero destino en el chat de GigaFiberPeru-Mensajes (+51 984 224 137). Si no llega, agrega el numero como destinatario de prueba en Meta Developer Console."
                )
            )
        } catch (e: IllegalArgumentException) {
            ResponseEntity
                .badRequest()
                .body(
                    WhatsAppTestSendResponseDto(
                        success = false,
                        message = "No se pudo enviar la plantilla por WhatsApp: ${e.message}",
                        recipient = null,
                        senderPhoneNumberId = whatsAppProperties.phoneNumberId,
                        metaResponse = "",
                        deliveryHint = ""
                    )
                )
        } catch (e: Exception) {
            ResponseEntity
                .status(500)
                .body(
                    WhatsAppTestSendResponseDto(
                        success = false,
                        message = "No se pudo enviar la plantilla por WhatsApp: ${friendlyTemplateErrorMessage(e.message)}",
                        recipient = null,
                        senderPhoneNumberId = whatsAppProperties.phoneNumberId,
                        metaResponse = e.message ?: "",
                        deliveryHint = ""
                    )
                )
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
