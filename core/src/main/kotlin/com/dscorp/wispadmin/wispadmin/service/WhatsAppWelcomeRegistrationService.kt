package com.dscorp.wispadmin.wispadmin.service

import com.dscorp.wispadmin.wispadmin.config.WhatsAppProperties
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionRepository
import com.dscorp.wispadmin.wispadmin.repository.WhatsAppMessageLogRepository
import com.dscorp.wispadmin.wispadmin.service.whatsapp.PeruvianPhoneValidator
import com.dscorp.wispadmin.wispadmin.service.whatsapp.WhatsAppSubscriptionRowMapper.welcomeSubscriptionFromRow
import com.dscorp.wispadmin.wispadmin.service.whatsapp.WelcomeVariableMapper
import com.dscorp.wispadmin.wispadmin.service.whatsapp.WhatsAppTemplateCatalog
import com.dscorp.wispadmin.wispadmin.service.whatsapp.WhatsAppTemplateCode
import com.dscorp.wispadmin.wispadmin.service.whatsapp.WhatsAppTemplateDeliveryService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

data class WelcomeSendResult(
    val subscriptionId: Int,
    val outcome: String,
    val detail: String? = null
)

@Service
class WhatsAppWelcomeRegistrationService(
    private val subscriptionRepository: SubscriptionRepository,
    private val whatsAppMessageLogRepository: WhatsAppMessageLogRepository,
    private val templateDeliveryService: WhatsAppTemplateDeliveryService,
    private val whatsAppProperties: WhatsAppProperties
) {

    private val log = LoggerFactory.getLogger(WhatsAppWelcomeRegistrationService::class.java)

    fun sendWelcomeIfApplicable(subscriptionId: Int) {
        try {
            sendWelcomeAndGetResult(subscriptionId)
        } catch (e: Exception) {
            log.error(
                "Error inesperado al procesar bienvenida WhatsApp para suscripcion {}: {}",
                subscriptionId,
                e.message,
                e
            )
        }
    }

    fun sendWelcomeAndGetResult(subscriptionId: Int): WelcomeSendResult {
        return try {
            sendWelcomeInternal(subscriptionId)
        } catch (e: Exception) {
            log.error(
                "Error inesperado al procesar bienvenida WhatsApp para suscripcion {}: {}",
                subscriptionId,
                e.message,
                e
            )
            WelcomeSendResult(
                subscriptionId = subscriptionId,
                outcome = OUTCOME_ERROR,
                detail = e.message
            )
        }
    }

    private fun sendWelcomeInternal(subscriptionId: Int): WelcomeSendResult {
        val definition = WhatsAppTemplateCatalog.get(WhatsAppTemplateCode.WELCOME_CUSTOMER)

        if (!whatsAppProperties.welcomeOnRegistration.enabled) {
            val reason = "Bienvenida desactivada en configuracion."
            log.warn("Bienvenida WhatsApp desactivada (whatsapp.welcome-on-registration.enabled=false).")
            persistSkipped(subscriptionId, null, reason, definition.messageType)
            return WelcomeSendResult(subscriptionId, OUTCOME_DISABLED, reason)
        }

        if (!whatsAppProperties.isConfigured()) {
            val reason = "WhatsApp Cloud API no esta configurado correctamente."
            log.warn(
                "WhatsApp no configurado para suscripcion {}: faltan api-version, phone-number-id, business-account-id o access-token.",
                subscriptionId
            )
            persistSkipped(subscriptionId, null, reason, definition.messageType)
            return WelcomeSendResult(subscriptionId, OUTCOME_NOT_CONFIGURED, reason)
        }

        if (whatsAppMessageLogRepository.existsBySubscriptionIdAndMessageTypeAndStatus(
                subscriptionId = subscriptionId,
                messageType = definition.messageType,
                status = WhatsAppTemplateDeliveryService.STATUS_SENT
            )
        ) {
            log.info("Bienvenida WhatsApp ya enviada para suscripcion {}", subscriptionId)
            return WelcomeSendResult(subscriptionId, OUTCOME_ALREADY_SENT, null)
        }

        val row = subscriptionRepository.findWhatsAppSubscriptionRowById(subscriptionId).firstOrNull()
        if (row == null) {
            val reason = "No se encontro la suscripcion $subscriptionId."
            log.warn("No se encontro suscripcion {} para bienvenida WhatsApp.", subscriptionId)
            persistSkipped(subscriptionId, null, reason, definition.messageType)
            return WelcomeSendResult(subscriptionId, OUTCOME_NOT_FOUND, reason)
        }

        val subscription = welcomeSubscriptionFromRow(row)
        val phone = subscription.phone

        if (phone.isNullOrBlank()) {
            persistSkipped(subscriptionId, phone, "El cliente no tiene telefono registrado.", definition.messageType)
            return WelcomeSendResult(subscriptionId, OUTCOME_SKIPPED, "El cliente no tiene telefono registrado.")
        }
        if (!PeruvianPhoneValidator.isValid(phone)) {
            persistSkipped(subscriptionId, phone, "El telefono debe ser un celular peruano valido.", definition.messageType)
            return WelcomeSendResult(subscriptionId, OUTCOME_SKIPPED, "El telefono debe ser un celular peruano valido.")
        }

        return try {
            val welcomeContext = WelcomeVariableMapper.buildContext(subscription)
            templateDeliveryService.deliverTemplate(
                definition = definition,
                subscription = subscription,
                phone = phone,
                subscriptionId = subscriptionId,
                welcomeContext = welcomeContext
            )
            log.info("Bienvenida WhatsApp enviada para suscripcion {} al telefono {}", subscriptionId, phone)
            WelcomeSendResult(subscriptionId, OUTCOME_SENT, phone)
        } catch (e: Exception) {
            log.error(
                "No se pudo enviar bienvenida WhatsApp para suscripcion {}: {}",
                subscriptionId,
                e.message,
                e
            )
            WelcomeSendResult(subscriptionId, OUTCOME_FAILED, e.message)
        }
    }

    private fun persistSkipped(
        subscriptionId: Int,
        phone: String?,
        reason: String,
        messageType: String
    ) {
        templateDeliveryService.persistLog(
            paymentId = null,
            subscriptionId = subscriptionId,
            phone = phone ?: "",
            messageType = messageType,
            message = reason,
            status = WhatsAppTemplateDeliveryService.STATUS_SKIPPED,
            errorMessage = reason
        )
        log.info("Bienvenida WhatsApp omitida para suscripcion {}: {}", subscriptionId, reason)
    }

    companion object {
        const val OUTCOME_SENT = "SENT"
        const val OUTCOME_SKIPPED = "SKIPPED"
        const val OUTCOME_FAILED = "FAILED"
        const val OUTCOME_ALREADY_SENT = "ALREADY_SENT"
        const val OUTCOME_NOT_FOUND = "NOT_FOUND"
        const val OUTCOME_DISABLED = "DISABLED"
        const val OUTCOME_NOT_CONFIGURED = "NOT_CONFIGURED"
        const val OUTCOME_ERROR = "ERROR"
    }
}
