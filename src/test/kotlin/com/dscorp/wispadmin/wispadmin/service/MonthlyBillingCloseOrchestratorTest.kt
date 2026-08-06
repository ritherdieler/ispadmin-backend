package com.dscorp.wispadmin.wispadmin.service

import com.dscorp.wispadmin.wispadmin.data.model.ScheduledTaskType
import com.dscorp.wispadmin.wispadmin.data.model.TaskExecutionStatus
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

class MonthlyBillingCloseOrchestratorTest {

    private val zone = ZoneId.of("America/Lima")
    private val subscriptionSnapshotService = mockk<MonthlySubscriptionSnapshotService>()
    private val collectsSnapshotService = mockk<MonthlyCollectsSnapshotService>()
    private val massBillingService = mockk<MonthlyMassBillingService>()
    private val scheduledTaskLogService = mockk<ScheduledTaskLogService>(relaxed = true)

    private val closedMonth = YearMonth.of(2026, 7)

    @Test
    fun `runMonthlyCloseIfLastDayOfMonth skips when not last day`() {
        val clock = clockOn(LocalDate.of(2026, 7, 15))
        val orchestrator = orchestrator(clock)

        assertNull(orchestrator.runMonthlyCloseIfLastDayOfMonth())

        verify(exactly = 0) { subscriptionSnapshotService.persistSnapshot(any()) }
    }

    @Test
    fun `runMonthlyClose executes steps in order and logs final close`() {
        every { subscriptionSnapshotService.persistSnapshot(closedMonth) } just runs
        every { collectsSnapshotService.persistSnapshot(closedMonth) } just runs
        every {
            massBillingService.generateMonthlyInvoices(closedMonth)
        } returns MonthlyMassBillingService.MassBillingResult(processedCount = 3, invoicesCreated = 3)

        val orchestrator = orchestrator(clockOn(LocalDate.of(2026, 7, 31)))

        val result = orchestrator.runMonthlyClose(closedMonth)

        verifyOrder {
            subscriptionSnapshotService.persistSnapshot(closedMonth)
            collectsSnapshotService.persistSnapshot(closedMonth)
            massBillingService.generateMonthlyInvoices(closedMonth)
        }
        assertEquals(TaskExecutionStatus.SUCCESS, result.overallStatus)
        verify(exactly = 1) { scheduledTaskLogService.logMonthlyBillingClose(result) }
    }

    @Test
    fun `runMonthlyClose still runs mass billing when subscription snapshot fails`() {
        every { subscriptionSnapshotService.persistSnapshot(closedMonth) } throws RuntimeException("sub down")
        every { collectsSnapshotService.persistSnapshot(closedMonth) } just runs
        every {
            massBillingService.generateMonthlyInvoices(closedMonth)
        } returns MonthlyMassBillingService.MassBillingResult(invoicesCreated = 1, processedCount = 1)

        val orchestrator = orchestrator(clockOn(LocalDate.of(2026, 7, 31)))
        val result = orchestrator.runMonthlyClose(closedMonth)

        verify(exactly = 1) {
            scheduledTaskLogService.logTaskError(
                ScheduledTaskType.MONTHLY_CLOSE_SUBSCRIPTION_SNAPSHOT,
                "sub down",
            )
        }
        verify(exactly = 1) { massBillingService.generateMonthlyInvoices(closedMonth) }
        assertEquals(TaskExecutionStatus.PARTIAL_SUCCESS, result.overallStatus)
    }

    @Test
    fun `runMonthlyClose logs mass billing failure and marks overall failed`() {
        every { subscriptionSnapshotService.persistSnapshot(closedMonth) } just runs
        every { collectsSnapshotService.persistSnapshot(closedMonth) } just runs
        every { massBillingService.generateMonthlyInvoices(closedMonth) } throws RuntimeException("billing fail")

        val orchestrator = orchestrator(clockOn(LocalDate.of(2026, 7, 31)))
        val result = orchestrator.runMonthlyClose(closedMonth)

        verify(exactly = 1) {
            scheduledTaskLogService.logTaskError(
                ScheduledTaskType.MONTHLY_CLOSE_MASS_BILLING,
                "billing fail",
            )
        }
        assertEquals(TaskExecutionStatus.FAILED, result.overallStatus)
        verify(exactly = 1) { scheduledTaskLogService.logMonthlyBillingClose(result) }
    }

    private fun orchestrator(clock: Clock): MonthlyBillingCloseOrchestrator =
        MonthlyBillingCloseOrchestrator(
            monthlySubscriptionSnapshotService = subscriptionSnapshotService,
            monthlyCollectsSnapshotService = collectsSnapshotService,
            monthlyMassBillingService = massBillingService,
            scheduledTaskLogService = scheduledTaskLogService,
            clock = clock,
        )

    private fun clockOn(date: LocalDate): Clock =
        Clock.fixed(date.atStartOfDay(zone).toInstant(), zone)
}
