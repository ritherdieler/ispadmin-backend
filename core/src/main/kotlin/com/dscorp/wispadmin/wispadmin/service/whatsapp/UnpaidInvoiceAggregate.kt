package com.dscorp.wispadmin.wispadmin.service.whatsapp

import com.dscorp.wispadmin.wispadmin.data.model.Payment
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

data class UnpaidInvoiceAggregate(
    val oldestPaymentId: Int,
    val totalAmount: Double,
    val invoiceCount: Int,
    val periodFrom: LocalDateTime,
    val periodTo: LocalDateTime,
) {
    fun periodSummary(): String = formatPeriodSummary(periodFrom, periodTo)

    companion object {
        private val DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy")

        fun fromUnpaidPayments(payments: List<Payment>): UnpaidInvoiceAggregate? {
            if (payments.isEmpty()) return null

            val ordered = payments.sortedWith(
                compareBy<Payment> { it.billingDateDatetime }.thenBy { it.id ?: Int.MAX_VALUE }
            )
            val oldest = ordered.first()
            val newest = ordered.last()
            val oldestId = oldest.id ?: return null

            return UnpaidInvoiceAggregate(
                oldestPaymentId = oldestId,
                totalAmount = payments.sumOf { it.amountToPay },
                invoiceCount = payments.size,
                periodFrom = oldest.billingDateDatetime,
                periodTo = newest.billingDateDatetime
            )
        }

        fun formatPeriodSummary(from: LocalDateTime, to: LocalDateTime): String {
            val start = from.format(DATE_FORMAT)
            val end = to.format(DATE_FORMAT)
            return if (start == end) start else "$start - $end"
        }
    }
}
