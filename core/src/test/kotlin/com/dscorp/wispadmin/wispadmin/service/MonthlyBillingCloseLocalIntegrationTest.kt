package com.dscorp.wispadmin.wispadmin.service

import com.dscorp.wispadmin.wispadmin.WispAdminApplication
import com.dscorp.wispadmin.wispadmin.data.model.ScheduledTaskType
import com.dscorp.wispadmin.wispadmin.data.model.TaskExecutionStatus
import com.dscorp.wispadmin.wispadmin.repository.MonthlyCollectsRepository
import com.dscorp.wispadmin.wispadmin.repository.PaymentRepository
import com.dscorp.wispadmin.wispadmin.repository.ScheduledTaskLogRepository
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionRepository
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionsStaticsRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.util.Date

@SpringBootTest(classes = [WispAdminApplication::class])
@ActiveProfiles("dev", "local")
@Tag("local-db")
class MonthlyBillingCloseLocalIntegrationTest {

    private val limaZone: ZoneId = ZoneId.of("America/Lima")

    @Autowired
    private lateinit var monthlySubscriptionSnapshotService: MonthlySubscriptionSnapshotService

    @Autowired
    private lateinit var monthlyCollectsSnapshotService: MonthlyCollectsSnapshotService

    @Autowired
    private lateinit var monthlyMassBillingService: MonthlyMassBillingService

    @Autowired
    private lateinit var monthlyBillingCloseOrchestrator: MonthlyBillingCloseOrchestrator

    @Autowired
    private lateinit var subscriptionsStaticsRepository: SubscriptionsStaticsRepository

    @Autowired
    private lateinit var monthlyCollectsRepository: MonthlyCollectsRepository

    @Autowired
    private lateinit var scheduledTaskLogRepository: ScheduledTaskLogRepository

    @Autowired
    private lateinit var paymentRepository: PaymentRepository

    @Autowired
    private lateinit var subscriptionRepository: SubscriptionRepository

    @Test
    fun springContextWiresMonthlyCloseBeans() {
        assertNotNull(monthlySubscriptionSnapshotService)
        assertNotNull(monthlyCollectsSnapshotService)
        assertNotNull(monthlyMassBillingService)
        assertNotNull(monthlyBillingCloseOrchestrator)
    }

    @Test
    fun computeSnapshotsReadOnlyAgainstLocalDevDatabase() {
        val closedMonth = YearMonth.now().minusMonths(1)

        val subscriptionSnapshot = monthlySubscriptionSnapshotService.computeSnapshot(closedMonth)
        assertTrue(subscriptionSnapshot.totalActiveSubscriptions >= 0)
        assertTrue(subscriptionSnapshot.newSubscriptions >= 0)
        assertTrue(subscriptionSnapshot.cancelledSubscriptions >= 0)

        val collectsSnapshot = monthlyCollectsSnapshotService.computeSnapshot(closedMonth)
        assertTrue(collectsSnapshot.grossIncome >= 0.0)
        assertTrue(collectsSnapshot.totalRaised >= 0.0)
        assertTrue(collectsSnapshot.totalDiscount >= 0.0)
        assertTrue(collectsSnapshot.totalReceivables >= 0.0)
        val sum =
            collectsSnapshot.totalRaised + collectsSnapshot.totalDiscount + collectsSnapshot.totalReceivables
        assertTrue(kotlin.math.abs(collectsSnapshot.grossIncome - sum) < 0.02)
    }

    @Test
    fun runMonthlyCloseIfLastDayOfMonthSkipsWhenTodayIsNotMonthEnd() {
        assertNull(monthlyBillingCloseOrchestrator.runMonthlyCloseIfLastDayOfMonth())
    }

    @Test
    fun persistSubscriptionSnapshot_writesSingleRowMatchingComputeSnapshot() {
        val closedMonth = YearMonth.now().minusMonths(1)
        val expected = monthlySubscriptionSnapshotService.computeSnapshot(closedMonth)

        monthlySubscriptionSnapshotService.persistSnapshot(closedMonth)

        val (from, to) = resumeMonthDateRange(closedMonth)
        val rows = subscriptionsStaticsRepository.findByDateInRange(from, to)
        assertEquals(1, rows.size)
        val row = rows.single()
        assertEquals(expected.totalActiveSubscriptions, row.totalActiveSubscriptions)
        assertEquals(expected.newSubscriptions, row.newSubscriptions)
        assertEquals(expected.cancelledSubscriptions, row.cancelledSubscriptions)
    }

    @Test
    fun persistCollectsSnapshot_writesRowThatPartitionsGrossIncome() {
        val closedMonth = YearMonth.now().minusMonths(1)
        val expected = monthlyCollectsSnapshotService.computeSnapshot(closedMonth)

        monthlyCollectsSnapshotService.persistSnapshot(closedMonth)

        val (from, to) = resumeMonthDateRange(closedMonth)
        val rows = monthlyCollectsRepository.findByDateInRange(from, to)
        assertEquals(1, rows.size)
        val row = rows.single()
        assertEquals(expected.grossIncome, row.grossIncome, 0.01)
        assertEquals(expected.totalRaised, row.totalRaised, 0.01)
        assertEquals(expected.totalDiscount, row.totalDiscount, 0.01)
        assertEquals(expected.totalReceivables, row.totalReceivables, 0.01)
    }

    @Test
    @Transactional
    fun processSubscription_persistsUnpaidInvoiceForFarFutureBillingMonth() {
        val closedMonth = YearMonth.of(2099, 12)
        val subscription = subscriptionRepository.findAllWithPlanForMassBilling()
            .firstOrNull { sub ->
                val id = sub.id ?: return@firstOrNull false
                paymentRepository.findPendingPaymentsBySubscriptionId(id) < 2
            }
            ?: return

        val subscriptionId = subscription.id!!
        val (billingStart, billingEnd) = monthlyMassBillingService.billingWindow(closedMonth)
        val before = paymentRepository
            .getLasMonthsPaymentMethodStatics(billingStart, billingEnd)
            .count { it.subscription?.id == subscriptionId }

        val step = monthlyMassBillingService.processSubscription(subscription, closedMonth)

        assertTrue(step.invoiceCreated || step.skippedDuplicate)
        val after = paymentRepository
            .getLasMonthsPaymentMethodStatics(billingStart, billingEnd)
            .count { it.subscription?.id == subscriptionId }
        if (step.invoiceCreated) {
            assertEquals(before + 1, after)
            val invoice = paymentRepository
                .getLasMonthsPaymentMethodStatics(billingStart, billingEnd)
                .filter { it.subscription?.id == subscriptionId }
                .maxBy { it.id ?: 0 }
            assertEquals(billingStart, invoice.billingDateDatetime)
            assertEquals(monthlyMassBillingService.dueDate(closedMonth), invoice.dueDate)
            assertEquals(false, invoice.paid)
        }
    }

    @Test
    fun generateMonthlyInvoices_processesBillableSubscriptionsAgainstDatabase() {
        val closedMonth = YearMonth.now().minusMonths(1)
        val billableCount = subscriptionRepository.findBillableSubscriptionIdsForMassBilling().size

        val result = monthlyMassBillingService.generateMonthlyInvoices(closedMonth)

        assertEquals(billableCount, result.processedCount)
        assertTrue(result.invoicesCreated + result.skippedDuplicateCount + result.cancelledCount <= result.processedCount)
    }

    @Test
    fun runMonthlyClose_endToEnd_persistsResumesMassBillingMetricsAndCloseLog() {
        val closedMonth = YearMonth.now().minusMonths(1)
        val billableCount = subscriptionRepository.findBillableSubscriptionIdsForMassBilling().size
        val logsBefore = scheduledTaskLogRepository
            .findByTaskTypeOrderByExecutionDateDesc(ScheduledTaskType.MONTHLY_BILLING_CLOSE)
            .size

        val result = monthlyBillingCloseOrchestrator.runMonthlyClose(closedMonth)

        assertEquals(TaskExecutionStatus.SUCCESS, result.overallStatus)
        assertEquals(TaskExecutionStatus.SUCCESS, result.subscriptionSnapshot.status)
        assertEquals(TaskExecutionStatus.SUCCESS, result.collectsSnapshot.status)
        assertEquals(TaskExecutionStatus.SUCCESS, result.massBilling.status)

        val processed = (result.massBilling.detail["processedCount"] as Number).toInt()
        val invoicesCreated = (result.massBilling.detail["invoicesCreated"] as Number).toInt()
        val skippedDuplicate = (result.massBilling.detail["skippedDuplicateCount"] as Number).toInt()
        assertEquals(billableCount, processed)
        assertTrue(invoicesCreated + skippedDuplicate + (result.massBilling.detail["cancelledCount"] as Number).toInt() <= processed)

        val (from, to) = resumeMonthDateRange(closedMonth)
        assertEquals(1, subscriptionsStaticsRepository.findByDateInRange(from, to).size)
        assertEquals(1, monthlyCollectsRepository.findByDateInRange(from, to).size)

        val logsAfter = scheduledTaskLogRepository
            .findByTaskTypeOrderByExecutionDateDesc(ScheduledTaskType.MONTHLY_BILLING_CLOSE)
        assertTrue(logsAfter.size > logsBefore)
        val latest = logsAfter.first()
        assertEquals(TaskExecutionStatus.SUCCESS, latest.status)
        assertNotNull(latest.detailedResult)
        assertTrue(latest.detailedResult!!.contains(closedMonth.toString()))
        assertTrue(latest.detailedResult!!.contains("massBilling"))
    }

    @Test
    fun runMonthlyClose_secondInvocation_skipsDuplicateInvoicesForSameBillingMonth() {
        val closedMonth = YearMonth.now().minusMonths(1)
        val (billingStart, billingEnd) = monthlyMassBillingService.billingWindow(closedMonth)

        val first = monthlyBillingCloseOrchestrator.runMonthlyClose(closedMonth)
        assertEquals(TaskExecutionStatus.SUCCESS, first.overallStatus)
        val createdFirst = (first.massBilling.detail["invoicesCreated"] as Number).toInt()

        val second = monthlyBillingCloseOrchestrator.runMonthlyClose(closedMonth)
        assertEquals(TaskExecutionStatus.SUCCESS, second.overallStatus)
        val createdSecond = (second.massBilling.detail["invoicesCreated"] as Number).toInt()
        val skippedSecond = (second.massBilling.detail["skippedDuplicateCount"] as Number).toInt()

        assertEquals(0, createdSecond)
        assertTrue(skippedSecond >= createdFirst)

        val paymentsInWindow = paymentRepository.getLasMonthsPaymentMethodStatics(billingStart, billingEnd).size
        assertTrue(paymentsInWindow >= createdFirst)
    }

    private fun resumeMonthDateRange(yearMonth: YearMonth): Pair<Date, Date> {
        val start = Date.from(yearMonth.atEndOfMonth().atStartOfDay(limaZone).toInstant())
        val end = Date.from(yearMonth.atEndOfMonth().plusDays(1).atStartOfDay(limaZone).toInstant())
        return start to end
    }
}
