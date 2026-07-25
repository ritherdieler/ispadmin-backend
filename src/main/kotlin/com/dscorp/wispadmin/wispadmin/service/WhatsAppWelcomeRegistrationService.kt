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

@Service
class WhatsAppWelcomeRegistrationService(
    private val subscriptionRepository: SubscriptionRepository,
    private val whatsAppMessageLogRepository: WhatsAppMessageLogRepository,
    private val templateDeliveryService: WhatsAppTemplateDeliveryService,
    private val whatsAppProperties: WhatsAppProperties
) {

    private val log = LoggerFactory.getLogger(WhatsAppWelcomeRegistrationService::class.java)

    fun sendWelcomeIfApplicable(subscriptionId: Int) {
        if (!whatsAppProperties.welcomeOnRegistration.enabled) {
            log.debug("Bienvenida WhatsApp desactivada por configuracion.")
            return
        }

        val definition = WhatsAppTemplateCatalog.get(WhatsAppTemplateCode.WELCOME_CUSTOMER)

        if (whatsAppMessageLogRepository.existsBySubscriptionIdAndMessageTypeAndStatus(
                subscriptionId = subscriptionId,
                messageType = definition.messageType,
                status = WhatsAppTemplateDeliveryService.STATUS_SENT
            )
        ) {
            log.info("Bienvenida WhatsApp ya enviada para suscripcion {}", subscriptionId)
            return
        }

        val row = subscriptionRepository.findWhatsAppSubscriptionRowById(subscriptionId).firstOrNull()
        if (row == null) {
            log.warn("No se encontro suscripcion {} para bienvenida WhatsApp.", subscriptionId)
            return
        }

        val subscription = welcomeSubscriptionFromRow(row)
        val phone = subscription.phone

        if (phone.isNullOrBlank()) {
            persistSkipped(subscriptionId, phone, "El cliente no tiene telefono registrado.", definition.messageType)
            return
        }
        if (!PeruvianPhoneValidator.isValid(phone)) {
            persistSkipped(subscriptionId, phone, "El telefono debe ser un celular peruano valido.", definition.messageType)
            return
        }

        try {
            val welcomeContext = WelcomeVariableMapper.buildContext(subscription)
            templateDeliveryService.deliverTemplate(
                definition = definition,
                subscription = subscription,
                phone = phone,
                subscriptionId = subscriptionId,
                welcomeContext = welcomeContext
            )
            log.info("Bienvenida WhatsApp enviada para suscripcion {} al telefono {}", subscriptionId, phone)
        } catch (e: Exception) {
            log.error("No se pudo enviar bienvenida WhatsApp para suscripcion {}: {}", subscriptionId, e.message)
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
}
