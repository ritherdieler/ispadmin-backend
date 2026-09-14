package com.dscorp.wispadmin.wispadmin.scheduled

import com.dscorp.wispadmin.wispadmin.data.model.ScheduledTaskType
import com.dscorp.wispadmin.wispadmin.dto.CutServiceResultDto
import com.dscorp.wispadmin.wispadmin.dto.CutServiceSummaryDto
import com.dscorp.wispadmin.wispadmin.service.ScheduledTaskLogService
import com.dscorp.wispadmin.wispadmin.service.SubscriptionService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

class CutServiceMonthlyTaskSchedulerTest {

    private val zone = ZoneId.of("America/Lima")
    private val subscriptionService = mockk<SubscriptionService>()
    private val scheduledTaskLogService = mockk<ScheduledTaskLogService>(relaxed = true)

    @Test
    fun `executeIfCutDay cuts when today is the Monday after a Saturday 15th`() {
        every { subscriptionService.cutInternetService() } returns emptyCutSummary()
        val scheduler = schedulerOn(LocalDate.of(2026, 8, 17))

        scheduler.executeIfCutDay()

        verify(exactly = 1) { subscriptionService.cutInternetService() }
    }

    @Test
    fun `executeIfCutDay does not cut on Saturday 15th`() {
        val scheduler = schedulerOn(LocalDate.of(2026, 8, 15))

        scheduler.executeIfCutDay()

        verify(exactly = 0) { subscriptionService.cutInternetService() }
    }

    @Test
    fun `executeIfCutDay does not cut on an unrelated Tuesday`() {
        val scheduler = schedulerOn(LocalDate.of(2026, 8, 18))

        scheduler.executeIfCutDay()

        verify(exactly = 0) { subscriptionService.cutInternetService() }
    }

    @Test
    fun `executeIfCutDay logs error when cut fails`() {
        every { subscriptionService.cutInternetService() } throws RuntimeException("mikrotik down")
        val scheduler = schedulerOn(LocalDate.of(2026, 7, 15))

        scheduler.executeIfCutDay()

        verify(exactly = 1) {
            scheduledTaskLogService.logTaskError(ScheduledTaskType.CUT_INTERNET_SERVICE, "mikrotik down")
        }
    }

    private fun schedulerOn(date: LocalDate): CutServiceMonthlyTaskScheduler {
        val instant = date.atStartOfDay(zone).toInstant()
        return CutServiceMonthlyTaskScheduler(
            subscriptionService = subscriptionService,
            scheduledTaskLogService = scheduledTaskLogService,
            clock = Clock.fixed(instant, zone),
        )
    }

    private fun emptyCutSummary(): CutServiceSummaryDto {
        val emptyResult = CutServiceResultDto(
            processedCount = 0,
            createdCount = 0,
            deletedCount = 0,
            alreadyExistsCount = 0,
            errorCount = 0,
            message = "",
            debtorsCount = 0,
            cancelledCount = 0,
            omittedByTvCable = 0,
        )
        return CutServiceSummaryDto(
            debtors = emptyResult,
            cancelled = emptyResult,
            totalProcessed = 0,
            totalCreated = 0,
            totalAlreadyExists = 0,
            totalErrors = 0,
            totalOmittedByTvCable = 0,
            message = "",
        )
    }
}
