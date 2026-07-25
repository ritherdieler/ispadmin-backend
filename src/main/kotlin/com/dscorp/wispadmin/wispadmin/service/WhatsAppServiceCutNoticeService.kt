package com.dscorp.wispadmin.wispadmin.service

import com.dscorp.wispadmin.wispadmin.config.WhatsAppProperties
import com.dscorp.wispadmin.wispadmin.data.model.Payment
import com.dscorp.wispadmin.wispadmin.data.model.Subscription
import com.dscorp.wispadmin.wispadmin.repository.PaymentRepository
import com.dscorp.wispadmin.wispadmin.repository.WhatsAppMessageLogRepository
import com.dscorp.wispadmin.wispadmin.service.whatsapp.PeruvianPhoneValidator
import com.dscorp.wispadmin.wispadmin.service.whatsapp.WhatsAppTemplateCatalog
import com.dscorp.wispadmin.wispadmin.service.whatsapp.WhatsAppTemplateCode
import com.dscorp.wispadmin.wispadmin.service.whatsapp.WhatsAppTemplateDeliveryService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.LocalDateTime

@Service
class WhatsAppServiceCutNoticeService(
    private val paymentRepository: PaymentRepository,
    private val whatsAppMessageLogRepository: WhatsAppMessageLogRepository,
    private val templateDeliveryService: WhatsAppTemplateDeliveryService,
    private val whatsAppProperties: WhatsAppProperties
) {

    private val log = LoggerFactory.getLogger(WhatsAppServiceCutNoticeService::class.java)

    fun sendCutNoticesForCandidates(subscriptions: List<Subscription>) {
        if (!whatsAppProperties.isConfigured()) {
            log.debug("WhatsApp no configurado; se omiten avisos de corte.")
            return
        }

        subscriptions.forEach { subscription ->
            runCatching { sendCutNoticeIfApplicable(subscription) }
                .onFailure { error ->
                    log.error(
                        "Error inesperado al enviar aviso de corte WhatsApp para suscripcion {}: {}",
                        subscription.id,
                        error.message
                    )
                }
        }
    }

    private fun sendCutNoticeIfApplicable(subscription: Subscription) {
        val subscriptionId = subscription.id ?: return
        val definition = WhatsAppTemplateCatalog.get(WhatsAppTemplateCode.SERVICE_CUT_NOTICE)
        val todayStart = LocalDate.now().atStartOfDay()
        val tomorrowStart = todayStart.plusDays(1)

        if (whatsAppMessageLogRepository.existsBySubscriptionIdAndMessageTypeAndStatusAndCreatedAtBetween(
                subscriptionId = subscriptionId,
                messageType = definition.messageType,
                status = WhatsAppTemplateDeliveryService.STATUS_SENT,
                startDate = todayStart,
                endDate = tomorrowStart
            )
        ) {
            log.info("Aviso de corte WhatsApp ya enviado hoy para suscripcion {}", subscriptionId)
            return
        }

        val phone = subscription.phone
        if (phone.isNullOrBlank()) {
            persistSkipped(subscriptionId, phone, "El cliente no tiene telefono registrado.", definition.messageType)
            return
        }
        if (!PeruvianPhoneValidator.isValid(phone)) {
            persistSkipped(
                subscriptionId,
                phone,
                "El telefono debe ser un celular peruano valido.",
                definition.messageType
            )
            return
        }

        val oldestUnpaid = paymentFromOldestUnpaidRow(subscriptionId)
        if (oldestUnpaid == null) {
            persistSkipped(
                subscriptionId,
                phone,
                "El cliente no tiene facturas pendientes.",
                definition.messageType
            )
            return
        }

        try {
            templateDeliveryService.deliverTemplate(
                definition = definition,
                subscription = subscription,
                phone = phone,
                oldestUnpaidPayment = oldestUnpaid,
                paymentId = oldestUnpaid.id,
                subscriptionId = subscriptionId
            )
            log.info("Aviso de corte WhatsApp enviado para suscripcion {} al telefono {}", subscriptionId, phone)
        } catch (e: Exception) {
            log.error("No se pudo enviar aviso de corte WhatsApp para suscripcion {}: {}", subscriptionId, e.message)
        }
    }

    private fun paymentFromOldestUnpaidRow(subscriptionId: Int): Payment? {
        val row = paymentRepository.findOldestUnpaidPaymentRow(subscriptionId).firstOrNull()
            ?: return null

        return Payment(
            discountAmount = 0.0,
            paid = false,
            amountToPay = (row[1] as Number).toDouble(),
            billingDateDatetime = when (val value = row.getOrNull(2)) {
                is LocalDateTime -> value
                is java.sql.Timestamp -> value.toLocalDateTime()
                else -> LocalDateTime.now()
            }
        ).apply {
            id = (row[0] as Number).toInt()
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
        log.info("Aviso de corte WhatsApp omitido para suscripcion {}: {}", subscriptionId, reason)
    }
}
