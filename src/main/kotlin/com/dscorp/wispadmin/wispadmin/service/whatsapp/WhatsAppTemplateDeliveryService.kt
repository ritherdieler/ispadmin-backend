package com.dscorp.wispadmin.wispadmin.service.whatsapp

import com.dscorp.wispadmin.wispadmin.data.model.Payment
import com.dscorp.wispadmin.wispadmin.data.model.Subscription
import com.dscorp.wispadmin.wispadmin.data.model.WhatsAppMessageLog
import com.dscorp.wispadmin.wispadmin.repository.WhatsAppMessageLogRepository
import com.dscorp.wispadmin.wispadmin.service.WhatsAppService
import org.springframework.stereotype.Service

@Service
class WhatsAppTemplateDeliveryService(
    private val whatsAppService: WhatsAppService,
    private val whatsAppMessageLogRepository: WhatsAppMessageLogRepository
) {

    fun deliverTemplate(
        definition: WhatsAppTemplateDefinition,
        subscription: Subscription,
        phone: String,
        payment: Payment? = null,
        oldestUnpaidPayment: Payment? = null,
        paymentId: Int? = null,
        subscriptionId: Int? = null
    ) {
        val parameters = TemplateParameterResolver.resolve(
            definition = definition,
            subscription = subscription,
            payment = payment,
            oldestUnpaidPayment = oldestUnpaidPayment
        )
        val previewMessage = buildPreviewMessage(definition, parameters)

        try {
            whatsAppService.sendTemplateMessage(
                phoneNumber = phone,
                templateName = definition.metaName,
                languageCode = definition.language,
                parameters = parameters
            )
            persistLog(
                paymentId = paymentId,
                subscriptionId = subscriptionId,
                phone = phone,
                messageType = definition.messageType,
                message = previewMessage,
                status = STATUS_SENT,
                errorMessage = null
            )
        } catch (e: Exception) {
            val friendlyError = WhatsAppMessageErrors.toFriendlyMessage(e.message)
            persistLog(
                paymentId = paymentId,
                subscriptionId = subscriptionId,
                phone = phone,
                messageType = definition.messageType,
                message = previewMessage,
                status = STATUS_FAILED,
                errorMessage = friendlyError
            )
            throw Exception(friendlyError, e)
        }
    }

    fun persistLog(
        paymentId: Int?,
        subscriptionId: Int?,
        phone: String,
        messageType: String,
        message: String,
        status: String,
        errorMessage: String?
    ) {
        whatsAppMessageLogRepository.save(
            WhatsAppMessageLog(
                paymentId = paymentId,
                subscriptionId = subscriptionId,
                phone = phone,
                messageType = messageType,
                status = status,
                message = message,
                errorMessage = errorMessage
            )
        )
    }

    fun buildPreviewMessage(
        definition: WhatsAppTemplateDefinition,
        parameters: List<NamedTemplateParameter>
    ): String {
        val paramsText = parameters.joinToString(", ") { "${it.parameterName}=${it.text}" }
        return "${definition.metaName} [$paramsText]"
    }

    companion object {
        const val STATUS_SENT = "SENT"
        const val STATUS_SKIPPED = "SKIPPED"
        const val STATUS_FAILED = "FAILED"
    }
}

object WhatsAppMessageErrors {
    fun toFriendlyMessage(errorMessage: String?): String {
        if (errorMessage.isNullOrBlank()) {
            return "No se pudo enviar el mensaje."
        }

        return when {
            errorMessage.contains("132001") ||
                errorMessage.contains("Template name does not exist", ignoreCase = true) ->
                "La plantilla de WhatsApp no existe, no esta aprobada o el idioma configurado no coincide."

            errorMessage.contains("131030") ||
                errorMessage.contains("not in allowed list", ignoreCase = true) ||
                errorMessage.contains("lista de autorizados", ignoreCase = true) ->
                "El numero no esta autorizado en Meta para pruebas."

            errorMessage.contains("401 Unauthorized", ignoreCase = true) ->
                "Token de WhatsApp invalido o vencido."

            errorMessage.contains("Unsupported post request", ignoreCase = true) ->
                "Phone Number ID de WhatsApp incorrecto o sin permisos para este token."

            else -> errorMessage
        }
    }
}
