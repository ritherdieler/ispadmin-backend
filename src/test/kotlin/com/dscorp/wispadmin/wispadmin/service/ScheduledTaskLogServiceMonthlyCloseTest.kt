package com.dscorp.wispadmin.wispadmin.service

import com.dscorp.wispadmin.wispadmin.data.model.ScheduledTaskLog
import com.dscorp.wispadmin.wispadmin.data.model.ScheduledTaskType
import com.dscorp.wispadmin.wispadmin.data.model.TaskExecutionStatus
import com.dscorp.wispadmin.wispadmin.dto.MonthlyBillingCloseResultDto
import com.dscorp.wispadmin.wispadmin.dto.MonthlyBillingCloseStepResultDto
import com.dscorp.wispadmin.wispadmin.repository.ScheduledTaskLogRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ScheduledTaskLogServiceMonthlyCloseTest {

    private val repository = mockk<ScheduledTaskLogRepository>()
    private val service = ScheduledTaskLogService(repository)

    @Test
    fun `logMonthlyBillingClose persists aggregate row with step errors in JSON`() {
        val result = MonthlyBillingCloseResultDto(
            closedMonth = "2026-07",
            overallStatus = TaskExecutionStatus.PARTIAL_SUCCESS,
            subscriptionSnapshot = MonthlyBillingCloseStepResultDto(
                step = "subscriptionSnapshot",
                status = TaskExecutionStatus.FAILED,
                error = "db timeout",
            ),
            collectsSnapshot = MonthlyBillingCloseStepResultDto(
                step = "collectsSnapshot",
                status = TaskExecutionStatus.SUCCESS,
            ),
            massBilling = MonthlyBillingCloseStepResultDto(
                step = "massBilling",
                status = TaskExecutionStatus.SUCCESS,
                detail = mapOf(
                    "processedCount" to 100,
                    "invoicesCreated" to 95,
                    "cancelledCount" to 2,
                ),
            ),
            message = "Cierre mensual 2026-07: PARTIAL_SUCCESS",
            errorMessage = "db timeout",
        )

        val saved = slot<ScheduledTaskLog>()
        every { repository.save(capture(saved)) } answers { saved.captured.copy(id = 42) }

        val log = service.logMonthlyBillingClose(result)

        assertEquals(42, log.id)
        assertEquals(ScheduledTaskType.MONTHLY_BILLING_CLOSE, saved.captured.taskType)
        assertEquals(TaskExecutionStatus.PARTIAL_SUCCESS, saved.captured.status)
        assertEquals(100, saved.captured.processedCount)
        assertEquals(95, saved.captured.createdCount)
        assertEquals(2, saved.captured.deletedCount)
        assertEquals(1, saved.captured.errorCount)
        assertEquals("db timeout", saved.captured.errorMessage)
        assertNotNull(saved.captured.detailedResult)
        assertTrue(saved.captured.detailedResult!!.contains("subscriptionSnapshot"))
    }
}
