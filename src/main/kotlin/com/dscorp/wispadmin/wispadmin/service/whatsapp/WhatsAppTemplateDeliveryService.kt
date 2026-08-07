package com.dscorp.wispadmin.wispadmin.service.whatsapp

import com.dscorp.wispadmin.wispadmin.data.model.Payment
import com.dscorp.wispadmin.wispadmin.data.model.Subscription
import com.dscorp.wispadmin.wispadmin.data.model.WhatsAppMarketingOptOut
import com.dscorp.wispadmin.wispadmin.data.model.WhatsAppMessageLog
import com.dscorp.wispadmin.wispadmin.repository.WhatsAppMarketingOptOutRepository
import com.dscorp.wispadmin.wispadmin.repository.WhatsAppMessageLogRepository
import com.dscorp.wispadmin.wispadmin.service.WhatsAppService
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class WhatsAppTemplateDeliveryService(
    private val whatsAppService: WhatsAppService,
    private val whatsAppMessageLogRepository: WhatsAppMessageLogRepository,
    private val templateDisplayService: WhatsAppTemplateDisplayService,
    private val marketingOptOutRepository: WhatsAppMarketingOptOutRepository,
    private val crmConversationServiceProvider: ObjectProvider<CrmConversationService>,
) {

    fun deliverTemplate(
        definition: WhatsAppTemplateDefinition,
        subscription: Subscription,
        phone: String,
        payment: Payment? = null,
        oldestUnpaidPayment: Payment? = null,
        paymentId: Int? = null,
        subscriptionId: Int? = null,
        welcomeContext: WelcomeTemplateContext? = null,
        campaignId: String? = null,
        operatorUsername: String? = null
    ): WhatsAppMessageLog {
        if (definition.category == WhatsAppTemplateCategory.MARKETING) {
            val normalizedPhone = PeruvianWhatsAppPhone.toInternational(phone)
            val optOut = marketingOptOutRepository.findByPhone(normalizedPhone)
            if (optOut?.status == WhatsAppMarketingOptOut.OPTED_OUT) {
                val reason = "El cliente $normalizedPhone opto por no recibir mensajes de marketing" +
                    (optOut.category?.let { " ($it)" } ?: "") + "."
                persistLog(
                    paymentId = paymentId,
                    subscriptionId = subscriptionId,
                    phone = PeruvianWhatsAppPhone.canonicalStoragePhone(phone),
                    messageType = definition.messageType,
                    message = reason,
                    status = STATUS_SKIPPED,
                    errorMessage = reason,
                    campaignId = campaignId,
                    operatorUsername = operatorUsername
                )
                throw IllegalStateException(reason)
            }
        }

        val parameters = TemplateParameterResolver.resolve(
            definition = definition,
            subscription = subscription,
            payment = payment,
            oldestUnpaidPayment = oldestUnpaidPayment,
            welcomeContext = welcomeContext
        )
        val buttonParameter = TemplateParameterResolver.resolveButtonParameter(
            definition = definition,
            subscription = subscription,
            payment = payment,
            oldestUnpaidPayment = oldestUnpaidPayment,
            welcomeContext = welcomeContext
        )?.let { resolvedButton ->
            WhatsAppTemplateButtonParameter(
                subType = definition.buttonParameter!!.subType,
                index = definition.buttonParameter!!.index,
                parameter = resolvedButton
            )
        }
        val previewMessage = templateDisplayService.buildLogPreview(definition, parameters)
        val callbackToken = java.util.UUID.randomUUID().toString()

        try {
            val result = whatsAppService.sendTemplateMessageWithMetaResponse(
                phoneNumber = phone,
                templateName = definition.metaName,
                languageCode = definition.language,
                parameters = parameters,
                callbackToken = callbackToken,
                buttonParameter = buttonParameter
            )
            // Proactive HSM must stamp lastOutboundAt so "Por atender" excludes unanswered templates.
            runCatching {
                crmConversationServiceProvider.ifAvailable?.touchOutbound(phone, createIfMissing = true)
            }
            return persistLog(
                paymentId = paymentId,
                subscriptionId = subscriptionId,
                phone = phone,
                messageType = definition.messageType,
                message = previewMessage,
                status = STATUS_SENT,
                errorMessage = null,
                metaMessageId = result.metaMessageId,
                campaignId = campaignId,
                operatorUsername = operatorUsername,
                callbackId = callbackToken
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
                errorMessage = friendlyError,
                metaMessageId = null,
                campaignId = campaignId,
                operatorUsername = operatorUsername,
                callbackId = callbackToken
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
        errorMessage: String?,
        metaMessageId: String? = null,
        campaignId: String? = null,
        operatorUsername: String? = null,
        callbackId: String? = null
    ): WhatsAppMessageLog {
        val now = LocalDateTime.now()
        val storedPhone = PeruvianWhatsAppPhone.canonicalStoragePhone(phone)
        return whatsAppMessageLogRepository.save(
            WhatsAppMessageLog(
                paymentId = paymentId,
                subscriptionId = subscriptionId,
                phone = storedPhone,
                messageType = messageType,
                status = status,
                message = message,
                errorMessage = errorMessage,
                metaMessageId = metaMessageId,
                sentAt = if (status == STATUS_SENT) now else null,
                failedAt = if (status == STATUS_FAILED) now else null,
                campaignId = campaignId,
                operatorUsername = operatorUsername,
                callbackId = callbackId
            )
        )
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

            errorMessage.contains("130429") ||
                errorMessage.contains("rate limit", ignoreCase = true) ->
                "Meta limito la velocidad de envio (throughput). Espera unos segundos y reintenta con un lote mas pequeno."

            errorMessage.contains("131049") ->
                "Meta no entrego el mensaje: el usuario ya recibio demasiados mensajes de marketing hoy."

            errorMessage.contains("131056") ||
                errorMessage.contains("pair rate", ignoreCase = true) ->
                "Meta limito envios al mismo numero. Espera antes de reenviar a ese cliente."

            errorMessage.contains("Unsupported post request", ignoreCase = true) ->
                "Phone Number ID de WhatsApp incorrecto o sin permisos para este token."

            else -> errorMessage
        }
    }
}
