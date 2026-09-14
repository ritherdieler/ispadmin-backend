package com.dscorp.wispadmin.wispadmin.controller

import com.dscorp.wispadmin.wispadmin.config.WhatsAppProperties
import com.dscorp.wispadmin.wispadmin.dto.WhatsAppTestSendResponseDto
import com.dscorp.wispadmin.wispadmin.requestbody.WhatsAppTemplateTestMessageRequest
import com.dscorp.wispadmin.wispadmin.service.WhatsAppService
import com.dscorp.wispadmin.wispadmin.service.whatsapp.NamedTemplateParameter
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Component

@Component
class WhatsAppTemplateMessageSender(
    private val whatsAppService: WhatsAppService,
    private val whatsAppProperties: WhatsAppProperties
) {

    fun sendPaymentReminderTemplate(
        request: WhatsAppTemplateTestMessageRequest
    ): ResponseEntity<WhatsAppTestSendResponseDto> {
        return try {
            val result = whatsAppService.sendTemplateMessageWithMetaResponse(
                phoneNumber = request.phoneNumber,
                templateName = whatsAppProperties.paymentReminderTemplateName,
                languageCode = whatsAppProperties.paymentReminderTemplateLanguage,
                parameters = listOf(
                    NamedTemplateParameter("customer_name", request.clientName),
                    NamedTemplateParameter("amount", request.amount),
                    NamedTemplateParameter("billing_period", request.billingPeriod)
                )
            )

            ResponseEntity.ok(
                WhatsAppTestSendResponseDto(
                    success = true,
                    message = "Plantilla enviada correctamente por WhatsApp.",
                    recipient = result.recipient,
                    senderPhoneNumberId = result.senderPhoneNumberId,
                    metaResponse = result.metaResponse,
                    deliveryHint = DELIVERY_HINT
                )
            )
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(failureResponse(e.message))
        } catch (e: Exception) {
            ResponseEntity.internalServerError().body(
                failureResponse(friendlyTemplateErrorMessage(e.message))
            )
        }
    }

    private fun failureResponse(message: String?) = WhatsAppTestSendResponseDto(
        success = false,
        message = "No se pudo enviar la plantilla por WhatsApp: ${message ?: "Error desconocido."}",
        recipient = null,
        senderPhoneNumberId = whatsAppProperties.phoneNumberId,
        metaResponse = "",
        deliveryHint = ""
    )

    private fun friendlyTemplateErrorMessage(errorMessage: String?): String {
        if (errorMessage.isNullOrBlank()) {
            return "No se pudo enviar la plantilla."
        }

        return when {
            errorMessage.contains("132001") ||
                errorMessage.contains("Template name does not exist", ignoreCase = true) ->
                "La plantilla de WhatsApp no existe, no esta aprobada o el idioma configurado no coincide."

            else -> errorMessage
        }
    }

    companion object {
        private const val DELIVERY_HINT =
            "Meta acepto el envio. Revisa WhatsApp del numero destino. " +
                "Si no llega, agrega el numero como destinatario de prueba en Meta Developer Console."
    }
}
