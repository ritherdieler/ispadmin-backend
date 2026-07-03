package com.dscorp.wispadmin.wispadmin.service

import com.dscorp.wispadmin.wispadmin.data.model.Payment
import com.dscorp.wispadmin.wispadmin.repository.PaymentRepository
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionRepository
import com.dscorp.wispadmin.wispadmin.requestbody.PaymentInvoiceCreateRequest
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Service
class PaymentInvoiceService(
    private val paymentRepository: PaymentRepository,
    private val subscriptionRepository: SubscriptionRepository
) {
    private fun parseBillingDate(value: String): LocalDateTime {
        return runCatching {
            LocalDateTime.parse(value)
        }.getOrElse {
            LocalDateTime.parse(value, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        }
    }

    fun createInvoice(request: PaymentInvoiceCreateRequest): Payment {
        require(request.amountToPay > 0) { "El monto a facturar debe ser mayor a 0" }

        val subscription = subscriptionRepository.findById(request.subscriptionId)
            .orElseThrow { IllegalArgumentException("Suscripcion no encontrada") }

        val billingDateDatetime = parseBillingDate(request.billingDate)
        val startOfMonth = billingDateDatetime
            .withDayOfMonth(1)
            .toLocalDate()
            .atStartOfDay()
        val endOfMonth = startOfMonth.plusMonths(1)

        val existsForMonth =
            paymentRepository.existsBySubscriptionIdAndBillingDateDatetimeGreaterThanEqualAndBillingDateDatetimeLessThan(
                request.subscriptionId,
                startOfMonth,
                endOfMonth
            )
        require(!existsForMonth) { "Ya existe una factura para ese mes y anio" }

        val payment = Payment(
            discountAmount = 0.0,
            discountReason = null,
            billingDateDatetime = billingDateDatetime,
            dueDate = null,
            method = null,
            amountPaid = 0.0,
            paid = false,
            subscription = subscription,
            responsible = null,
            isPaymentCommit = false,
            amountToPay = request.amountToPay,
            electronicPayerName = null
        )

        return paymentRepository.save(payment)
    }
}
