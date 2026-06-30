package com.dscorp.wispadmin.wispadmin.scheduled

import com.dscorp.wispadmin.wispadmin.service.PaymentWhatsAppNotificationService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class MonthlyWhatsAppReminderScheduler(
    private val paymentWhatsAppNotificationService: PaymentWhatsAppNotificationService
) {

    private val logger = LoggerFactory.getLogger(MonthlyWhatsAppReminderScheduler::class.java)

    @Scheduled(cron = "0 0 9 1 * *", zone = "America/Lima")
    fun sendMonthlyPaymentReminders() {
        logger.info("Iniciando proceso mensual de recordatorios WhatsApp.")

        try {
            val result = paymentWhatsAppNotificationService.sendMonthlyPaymentReminders()
            logger.info(
                "Proceso mensual de recordatorios WhatsApp finalizado. candidates={}, sent={}, skipped={}, failed={}",
                result.candidates,
                result.sent,
                result.skipped,
                result.failed
            )
        } catch (e: Exception) {
            logger.error("Error al ejecutar proceso mensual de recordatorios WhatsApp.", e)
        }
    }
}
