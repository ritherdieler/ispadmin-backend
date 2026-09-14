package com.dscorp.wispadmin.wispadmin.service.whatsapp

import com.dscorp.wispadmin.wispadmin.data.model.Payment
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class UnpaidInvoiceAggregateTest {

    @Test
    fun consolidatesThreeUnpaidInvoicesIntoTotalCountPeriodRangeAndOldestAnchor() {
        val unpaid = listOf(
            unpaidPayment(id = 101, amount = 80.0, year = 2026, month = 7, day = 1),
            unpaidPayment(id = 102, amount = 80.0, year = 2026, month = 8, day = 1),
            unpaidPayment(id = 103, amount = 80.0, year = 2026, month = 9, day = 1),
        )

        val aggregate = UnpaidInvoiceAggregate.fromUnpaidPayments(unpaid)

        assertEquals(240.0, aggregate?.totalAmount)
        assertEquals(3, aggregate?.invoiceCount)
        assertEquals(101, aggregate?.oldestPaymentId)
        assertEquals(LocalDateTime.of(2026, 7, 1, 0, 0), aggregate?.periodFrom)
        assertEquals(LocalDateTime.of(2026, 9, 1, 0, 0), aggregate?.periodTo)
        assertEquals("01/07/2026 - 01/09/2026", aggregate?.periodSummary())
    }

    @Test
    fun usesOldestBillingDateThenLowestIdAsAnchorWhenDatesTie() {
        val unpaid = listOf(
            unpaidPayment(id = 20, amount = 50.0, year = 2026, month = 7, day = 1),
            unpaidPayment(id = 10, amount = 50.0, year = 2026, month = 7, day = 1),
        )

        val aggregate = UnpaidInvoiceAggregate.fromUnpaidPayments(unpaid)

        assertEquals(10, aggregate?.oldestPaymentId)
        assertEquals(100.0, aggregate?.totalAmount)
        assertEquals("01/07/2026", aggregate?.periodSummary())
    }

    @Test
    fun returnsNullWhenThereAreNoUnpaidInvoices() {
        assertNull(UnpaidInvoiceAggregate.fromUnpaidPayments(emptyList()))
    }

    private fun unpaidPayment(id: Int, amount: Double, year: Int, month: Int, day: Int): Payment {
        return Payment(
            discountAmount = 0.0,
            paid = false,
            amountToPay = amount,
            billingDateDatetime = LocalDateTime.of(year, month, day, 0, 0)
        ).apply { this.id = id }
    }
}
