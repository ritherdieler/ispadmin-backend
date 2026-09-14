package com.dscorp.wispadmin.wispadmin.controller

import com.dscorp.wispadmin.wispadmin.dto.WhatsAppTestSendResponseDto
import com.dscorp.wispadmin.wispadmin.requestbody.WhatsAppTemplateTestMessageRequest
import com.dscorp.wispadmin.wispadmin.requestbody.WhatsAppTestMessageRequest
import com.dscorp.wispadmin.wispadmin.service.WhatsAppService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/whatsapp")
class WhatsAppController(
    private val whatsAppService: WhatsAppService,
    private val templateMessageSender: WhatsAppTemplateMessageSender
) {

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
