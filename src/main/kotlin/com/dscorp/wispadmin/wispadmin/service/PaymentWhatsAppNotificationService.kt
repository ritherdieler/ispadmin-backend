package com.dscorp.wispadmin.wispadmin.service

import com.dscorp.wispadmin.wispadmin.repository.PaymentRepository
import org.springframework.stereotype.Service
import java.time.format.DateTimeFormatter
import com.dscorp.wispadmin.wispadmin.dto.WhatsAppReminderBatchResultDto
import com.dscorp.wispadmin.wispadmin.dto.WhatsAppReminderResultDto
import com.dscorp.wispadmin.wispadmin.data.model.WhatsAppMessageLog
import com.dscorp.wispadmin.wispadmin.repository.WhatsAppMessageLogRepository
import java.time.LocalDate
@Service
class PaymentWhatsAppNotificationService(
    private val paymentRepository: PaymentRepository,
    private val whatsAppService: WhatsAppService,
    private val whatsAppMessageLogRepository: WhatsAppMessageLogRepository
) {

    // Busca una factura pendiente y envia un mensaje de cobranza por WhatsApp al cliente.
    fun sendPaymentReminder(paymentId: Int): Boolean {
        val payment = paymentRepository.findById(paymentId).orElseThrow {
            Exception("Factura no encontrada.")
        }
        val todayStart = LocalDate.now().atStartOfDay()
        val tomorrowStart = todayStart.plusDays(1)
        if (
            whatsAppMessageLogRepository.existsByPaymentIdAndMessageTypeAndStatusAndCreatedAtBetween(
                paymentId = paymentId,
                messageType = "PAYMENT_REMINDER",
                status = "SENT",
                startDate = todayStart,
                endDate = tomorrowStart
            )
        ) {
            throw IllegalStateException("Ya se envio un recordatorio de pago por WhatsApp hoy.")
        }
        if (payment.paid) {
            throw Exception("La factura ya se encuentra pagada.")
        }

        val subscription = payment.subscription ?: throw Exception("La factura no tiene cliente asociado.")

        val phone = subscription.phone
            ?.takeIf { it.isNotBlank() }
            ?: throw Exception("El cliente no tiene telefono registrado.")

        val clientName = subscription.getFullName()
        val amount = payment.amountToPay
        val billingDate = payment.billingDateDatetime.format(
            DateTimeFormatter.ofPattern("dd/MM/yyyy")
        )

        val message = buildPaymentReminderMessage(
            clientName = clientName,
            amount = amount,
            billingDate = billingDate
        )

        return try {
            val sent = whatsAppService.sendTextMessage(
                phoneNumber = phone,
                message = message
            )

            whatsAppMessageLogRepository.save(
                WhatsAppMessageLog(
                    paymentId = payment.id,
                    subscriptionId = subscription.id,
                    phone = phone,
                    messageType = "PAYMENT_REMINDER",
                    status = "SENT",
                    message = message,
                    errorMessage = null
                )
            )

            sent
        } catch (e: Exception) {
            val friendlyError = friendlyWhatsAppErrorMessage(e.message)

            whatsAppMessageLogRepository.save(
                WhatsAppMessageLog(
                    paymentId = payment.id,
                    subscriptionId = subscription.id,
                    phone = phone,
                    messageType = "PAYMENT_REMINDER",
                    status = "FAILED",
                    message = message,
                    errorMessage = friendlyError
                )
            )

            throw Exception(friendlyError, e)
        }
    }

    // Envia recordatorios de pago a varias facturas pendientes.
// Devuelve un resumen detallado para saber cuales se enviaron y cuales fallaron.
    fun sendPendingPaymentReminders(limit: Int): WhatsAppReminderBatchResultDto {
        val safeLimit = limit.coerceIn(1, 20)
        val pendingPayments = paymentRepository.findPendingPaymentsWithPhone(safeLimit)

        val details = mutableListOf<WhatsAppReminderResultDto>()

        var sentCount = 0
        var skippedCount = 0
        var failedCount = 0

        pendingPayments.forEach { payment ->
            val paymentId = payment.id
            val subscription = payment.subscription
            val subscriptionId = subscription?.id
            val phone = subscription?.phone

            if (paymentId == null) {
                skippedCount++

                details.add(
                    WhatsAppReminderResultDto(
                        paymentId = 0,
                        subscriptionId = subscriptionId,
                        phone = phone,
                        status = "SKIPPED",
                        reason = "La factura no tiene ID."
                    )
                )

                return@forEach
            }

            if (subscription == null) {
                skippedCount++

                details.add(
                    WhatsAppReminderResultDto(
                        paymentId = paymentId,
                        subscriptionId = null,
                        phone = phone,
                        status = "SKIPPED",
                        reason = "La factura no tiene cliente asociado."
                    )
                )

                return@forEach
            }

            if (phone.isNullOrBlank()) {
                skippedCount++

                details.add(
                    WhatsAppReminderResultDto(
                        paymentId = paymentId,
                        subscriptionId = subscriptionId,
                        phone = phone,
                        status = "SKIPPED",
                        reason = "El cliente no tiene telefono registrado."
                    )
                )

                return@forEach
            }

            try {
                sendPaymentReminder(paymentId)
                sentCount++

                details.add(
                    WhatsAppReminderResultDto(
                        paymentId = paymentId,
                        subscriptionId = subscriptionId,
                        phone = phone,
                        status = "SENT",
                        reason = "Recordatorio enviado correctamente."
                    )
                )
            } catch (e: IllegalStateException) {
                skippedCount++

                details.add(
                    WhatsAppReminderResultDto(
                        paymentId = paymentId,
                        subscriptionId = subscriptionId,
                        phone = phone,
                        status = "SKIPPED",
                        reason = e.message ?: "Ya se envio un recordatorio de pago por WhatsApp hoy."
                    )
                )
            } catch (e: Exception) {
                failedCount++

                details.add(
                    WhatsAppReminderResultDto(
                        paymentId = paymentId,
                        subscriptionId = subscriptionId,
                        phone = phone,
                        status = "FAILED",
                        reason = e.message ?: "No se pudo enviar el recordatorio."
                    )
                )
            }
        }

        return WhatsAppReminderBatchResultDto(
            requestedLimit = safeLimit,
            candidates = pendingPayments.size,
            sent = sentCount,
            skipped = skippedCount,
            failed = failedCount,
            details = details
        )
    }

    // Construye el mensaje de recordatorio de pago que se enviara al cliente por WhatsApp.
    // Mantiene un texto claro y formal para que pueda usarse en pruebas reales con clientes.
    private fun buildPaymentReminderMessage(
        clientName: String,
        amount: Double,
        billingDate: String
    ): String {
        return """
        Hola $clientName, le saluda GigaFiber Peru.

        Le recordamos que tiene una factura pendiente:

        Monto pendiente: S/ $amount
        Periodo de facturacion: $billingDate

        Puede realizar su pago por los medios disponibles de la empresa.

        Si ya realizo el pago, por favor ignore este mensaje.
        Gracias por su preferencia.
    """.trimIndent()
    }

    private fun friendlyWhatsAppErrorMessage(errorMessage: String?): String {
        if (errorMessage.isNullOrBlank()) {
            return "No se pudo enviar el recordatorio."
        }

        return when {
            errorMessage.contains("132001") ||
                    errorMessage.contains("Template name does not exist", ignoreCase = true) -> {
                "La plantilla de WhatsApp no existe, no esta aprobada o el idioma configurado no coincide."
            }

            errorMessage.contains("131030") ||
                    errorMessage.contains("not in allowed list", ignoreCase = true) ||
                    errorMessage.contains("lista de autorizados", ignoreCase = true) -> {
                "El numero no esta autorizado en Meta para pruebas."
            }

            errorMessage.contains("401 Unauthorized", ignoreCase = true) -> {
                "Token de WhatsApp invalido o vencido."
            }

            errorMessage.contains("Unsupported post request", ignoreCase = true) -> {
                "Phone Number ID de WhatsApp incorrecto o sin permisos para este token."
            }

            else -> errorMessage
        }
    }

}
