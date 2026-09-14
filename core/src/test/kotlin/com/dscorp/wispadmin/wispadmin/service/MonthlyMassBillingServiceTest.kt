package com.dscorp.wispadmin.wispadmin.service

import com.dscorp.wispadmin.wispadmin.data.model.Payment
import com.dscorp.wispadmin.wispadmin.data.model.Plan
import com.dscorp.wispadmin.wispadmin.data.model.EquipmentCondition
import com.dscorp.wispadmin.wispadmin.data.model.ServiceStatus
import com.dscorp.wispadmin.wispadmin.data.model.Subscription
import com.dscorp.wispadmin.wispadmin.data.model.SubscriptionActionType
import com.dscorp.wispadmin.wispadmin.data.model.SubscriptionLog
import com.dscorp.wispadmin.wispadmin.repository.PaymentRepository
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionLogRepository
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.time.YearMonth

class MonthlyMassBillingServiceTest {

    private val subscriptionRepository = mockk<SubscriptionRepository>()
    private val paymentRepository = mockk<PaymentRepository>(relaxed = true)
    private val subscriptionLogRepository = mockk<SubscriptionLogRepository>(relaxed = true)

    private val service = MonthlyMassBillingService(
        subscriptionRepository = subscriptionRepository,
        paymentRepository = paymentRepository,
        subscriptionLogRepository = subscriptionLogRepository,
    )

    @Test
    fun `processSubscription creates invoice when no pending cancellation and no duplicate in billing window`() {
        val plan = Plan(id = 1, name = "50M", price = 79.0)
        val subscription = subscription(id = 5, plan = plan, status = ServiceStatus.ACTIVE)
        every { paymentRepository.findPendingPaymentsBySubscriptionId(5) } returns 0
        every {
            paymentRepository.existsBySubscriptionIdAndBillingDateDatetimeGreaterThanEqualAndBillingDateDatetimeLessThan(
                5,
                LocalDateTime.of(2026, 7, 31, 0, 0),
                LocalDateTime.of(2026, 8, 31, 0, 0),
            )
        } returns false

        val paymentSlot = slot<Payment>()
        every { paymentRepository.save(capture(paymentSlot)) } answers { firstArg() }
        every { subscriptionRepository.applyMassBillingInvoiceSubscriptionState(5) } returns Unit

        val result = service.processSubscription(subscription, YearMonth.of(2026, 7))

        assertTrue(result.invoiceCreated)
        assertEquals(79.0, paymentSlot.captured.amountToPay)
        assertEquals(LocalDateTime.of(2026, 7, 31, 0, 0), paymentSlot.captured.billingDateDatetime)
        assertEquals(LocalDateTime.of(2026, 7, 15, 0, 0), paymentSlot.captured.dueDate)
        assertFalse(paymentSlot.captured.paid)
        verify(exactly = 1) { subscriptionRepository.applyMassBillingInvoiceSubscriptionState(5) }
    }

    @Test
    fun `processSubscription cancels when two or more unpaid invoices`() {
        val plan = Plan(id = 2, name = "100M", price = 99.0)
        val subscription = subscription(id = 8, plan = plan, status = ServiceStatus.ACTIVE)
        every { paymentRepository.findPendingPaymentsBySubscriptionId(8) } returns 2
        every { subscriptionRepository.applyMassBillingCancellation(8, any()) } returns Unit

        val logSlot = slot<SubscriptionLog>()
        every { subscriptionLogRepository.save(capture(logSlot)) } answers { firstArg() }

        val result = service.processSubscription(subscription, YearMonth.of(2026, 7))

        assertTrue(result.cancelledByUnpaid)
        assertEquals(SubscriptionActionType.CANCELED_BY_STORED_PROCEDURE, logSlot.captured.actionType)
        assertEquals("store_procedure", logSlot.captured.responsibleId)
        verify(exactly = 0) { paymentRepository.save(any()) }
    }

    @Test
    fun `processSubscription skips invoice when billing window already has payment`() {
        val plan = Plan(id = 3, name = "30M", price = 59.0)
        val subscription = subscription(id = 9, plan = plan, status = ServiceStatus.ACTIVE)
        every { paymentRepository.findPendingPaymentsBySubscriptionId(9) } returns 1
        every {
            paymentRepository.existsBySubscriptionIdAndBillingDateDatetimeGreaterThanEqualAndBillingDateDatetimeLessThan(
                any(),
                any(),
                any(),
            )
        } returns true

        val result = service.processSubscription(subscription, YearMonth.of(2026, 7))

        assertTrue(result.skippedDuplicate)
        verify(exactly = 0) { paymentRepository.save(any()) }
    }

    @Test
    fun `generateMonthlyInvoices aggregates results over billable subscriptions`() {
        val plan = Plan(id = 1, name = "50M", price = 79.0)
        val sub = subscription(id = 1, plan = plan, status = ServiceStatus.ACTIVE)
        every { subscriptionRepository.findBillableSubscriptionIdsForMassBilling() } returns listOf(1)
        every { subscriptionRepository.findWithPlanByIdForMassBilling(1) } returns java.util.Optional.of(sub)
        every { paymentRepository.findPendingPaymentsBySubscriptionId(1) } returns 0
        every {
            paymentRepository.existsBySubscriptionIdAndBillingDateDatetimeGreaterThanEqualAndBillingDateDatetimeLessThan(
                any(),
                any(),
                any(),
            )
        } returns false
        every { paymentRepository.save(any()) } answers { firstArg() }
        every { subscriptionRepository.applyMassBillingInvoiceSubscriptionState(1) } returns Unit

        val result = service.generateMonthlyInvoices(YearMonth.of(2026, 7))

        assertEquals(1, result.processedCount)
        assertEquals(1, result.invoicesCreated)
    }

    private fun subscription(id: Int, plan: Plan, status: ServiceStatus): Subscription {
        return Subscription(
            id = id,
            plan = plan,
            serviceStatus = status,
            equipmentCondition = EquipmentCondition.LOAN,
        )
    }
}
