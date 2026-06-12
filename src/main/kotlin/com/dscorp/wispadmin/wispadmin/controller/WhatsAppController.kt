package com.dscorp.wispadmin.wispadmin.controller

import com.dscorp.wispadmin.wispadmin.requestbody.WhatsAppTestMessageRequest
import com.dscorp.wispadmin.wispadmin.service.WhatsAppService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/whatsapp")
class WhatsAppController(
    private val whatsAppService: WhatsAppService
) {

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
        } catch (e: Exception) {
            ResponseEntity
                .status(500)
                .body("No se pudo enviar el mensaje por WhatsApp: ${e.message}")
        }
    }
}