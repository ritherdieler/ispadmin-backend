package com.dscorp.wispadmin.wispadmin.service

import com.dscorp.wispadmin.wispadmin.repository.PaymentRepository
import org.springframework.stereotype.Service
import java.time.format.DateTimeFormatter
import com.dscorp.wispadmin.wispadmin.dto.WhatsAppReminderBatchResultDto
import com.dscorp.wispadmin.wispadmin.dto.WhatsAppReminderResultDto

@Service
class PaymentWhatsAppNotificationService(
    private val paymentRepository: PaymentRepository,
    private val whatsAppService: WhatsAppService
) {

    // Busca una factura pendiente y envia un mensaje de cobranza por WhatsApp al cliente.
    fun sendPaymentReminder(paymentId: Int): Boolean {
        val payment = paymentRepository.findById(paymentId).orElseThrow {
            Exception("Factura no encontrada.")
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

        return whatsAppService.sendTextMessage(
            phoneNumber = phone,
            message = message
        )
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
}