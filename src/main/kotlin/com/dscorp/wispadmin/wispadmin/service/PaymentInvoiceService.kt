package com.dscorp.wispadmin.wispadmin.service

import com.dscorp.wispadmin.wispadmin.data.model.Payment
import com.dscorp.wispadmin.wispadmin.repository.PaymentRepository
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionRepository
import com.dscorp.wispadmin.wispadmin.requestbody.PaymentInvoiceCreateRequest
import org.springframework.stereotype.Service
import java.util.Calendar
import java.time.Instant
import java.time.ZoneId
@Service
class PaymentInvoiceService(
    private val paymentRepository: PaymentRepository,
    private val subscriptionRepository: SubscriptionRepository
) {
    fun createInvoice(request: PaymentInvoiceCreateRequest): Payment {
        require(request.amountToPay > 0) { "El monto a facturar debe ser mayor a 0" }

        val subscription = subscriptionRepository.findById(request.subscriptionId)
            .orElseThrow { IllegalArgumentException("Suscripción no encontrada") }

        val calendar = Calendar.getInstance().apply {
            timeInMillis = request.billingDate
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            set(Calendar.DAY_OF_MONTH, 1)
        }
        val startOfMonth = calendar.toInstant()
            .atZone(ZoneId.systemDefault())
            .toLocalDateTime()

        calendar.add(Calendar.MONTH, 1)
        calendar.add(Calendar.MILLISECOND, -1)

        val endOfMonth = calendar.toInstant()
            .atZone(ZoneId.systemDefault())
            .toLocalDateTime()

        val existsForMonth = paymentRepository.existsBySubscriptionIdAndBillingDateDatetimeBetween(
            request.subscriptionId,
            startOfMonth,
            endOfMonth
        )
        require(!existsForMonth) { "Ya existe una factura para ese mes y año" }

        val payment = Payment(
            discountAmount = 0.0,
            discountReason = null,
            billingDateDatetime = Instant.ofEpochMilli(request.billingDate)
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime(),
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
