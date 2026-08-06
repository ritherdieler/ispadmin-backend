package com.dscorp.wispadmin.wispadmin.service

import com.dscorp.wispadmin.wispadmin.data.model.ScheduledTaskType
import com.dscorp.wispadmin.wispadmin.data.model.TaskExecutionStatus
import com.dscorp.wispadmin.wispadmin.dto.MonthlyBillingCloseResultDto
import com.dscorp.wispadmin.wispadmin.dto.MonthlyBillingCloseStepResultDto
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

@Service
class MonthlyBillingCloseOrchestrator(
    private val monthlySubscriptionSnapshotService: MonthlySubscriptionSnapshotService,
    private val monthlyCollectsSnapshotService: MonthlyCollectsSnapshotService,
    private val monthlyMassBillingService: MonthlyMassBillingService,
    private val scheduledTaskLogService: ScheduledTaskLogService,
    private val clock: Clock = Clock.system(ZoneId.of("America/Lima")),
) {

    private val log = LoggerFactory.getLogger(javaClass)
    private val zone: ZoneId = ZoneId.of("America/Lima")

    fun runMonthlyCloseIfLastDayOfMonth(): MonthlyBillingCloseResultDto? {
        val today = LocalDate.now(clock.withZone(zone))
        if (today.dayOfMonth != today.lengthOfMonth()) {
            return null
        }
        return runMonthlyClose(YearMonth.from(today))
    }

    fun runMonthlyClose(closedMonth: YearMonth): MonthlyBillingCloseResultDto {
        var subscriptionStep = successStep("subscriptionSnapshot")
        var collectsStep = successStep("collectsSnapshot")
        var massBillingStep = successStep("massBilling")

        try {
            monthlySubscriptionSnapshotService.persistSnapshot(closedMonth)
        } catch (e: Exception) {
            log.error("Monthly close subscription snapshot failed: {}", e.message, e)
            scheduledTaskLogService.logTaskError(
                ScheduledTaskType.MONTHLY_CLOSE_SUBSCRIPTION_SNAPSHOT,
                e.message ?: e.javaClass.simpleName,
            )
            subscriptionStep = failedStep("subscriptionSnapshot", e)
        }

        try {
            monthlyCollectsSnapshotService.persistSnapshot(closedMonth)
        } catch (e: Exception) {
            log.error("Monthly close collects snapshot failed: {}", e.message, e)
            scheduledTaskLogService.logTaskError(
                ScheduledTaskType.MONTHLY_CLOSE_COLLECTS_SNAPSHOT,
                e.message ?: e.javaClass.simpleName,
            )
            collectsStep = failedStep("collectsSnapshot", e)
        }

        try {
            val billingResult = monthlyMassBillingService.generateMonthlyInvoices(closedMonth)
            massBillingStep = successStep(
                "massBilling",
                mapOf(
                    "processedCount" to billingResult.processedCount,
                    "invoicesCreated" to billingResult.invoicesCreated,
                    "cancelledCount" to billingResult.cancelledCount,
                    "skippedDuplicateCount" to billingResult.skippedDuplicateCount,
                ),
            )
        } catch (e: Exception) {
            log.error("Monthly close mass billing failed: {}", e.message, e)
            scheduledTaskLogService.logTaskError(
                ScheduledTaskType.MONTHLY_CLOSE_MASS_BILLING,
                e.message ?: e.javaClass.simpleName,
            )
            massBillingStep = failedStep("massBilling", e)
        }

        val overall = overallStatus(subscriptionStep, collectsStep, massBillingStep)
        val errorParts = listOfNotNull(
            subscriptionStep.error,
            collectsStep.error,
            massBillingStep.error,
        )
        val result = MonthlyBillingCloseResultDto(
            closedMonth = closedMonth.toString(),
            overallStatus = overall,
            subscriptionSnapshot = subscriptionStep,
            collectsSnapshot = collectsStep,
            massBilling = massBillingStep,
            message = "Cierre mensual $closedMonth: $overall",
            errorMessage = errorParts.takeIf { it.isNotEmpty() }?.joinToString(" | "),
        )
        scheduledTaskLogService.logMonthlyBillingClose(result)
        return result
    }

    private fun successStep(name: String, detail: Map<String, Any?> = emptyMap()) =
        MonthlyBillingCloseStepResultDto(
            step = name,
            status = TaskExecutionStatus.SUCCESS,
            detail = detail,
        )

    private fun failedStep(name: String, e: Exception) =
        MonthlyBillingCloseStepResultDto(
            step = name,
            status = TaskExecutionStatus.FAILED,
            error = e.message ?: e.javaClass.simpleName,
        )

    private fun overallStatus(
        subscription: MonthlyBillingCloseStepResultDto,
        collects: MonthlyBillingCloseStepResultDto,
        massBilling: MonthlyBillingCloseStepResultDto,
    ): TaskExecutionStatus {
        if (massBilling.status == TaskExecutionStatus.FAILED) {
            return TaskExecutionStatus.FAILED
        }
        if (subscription.status == TaskExecutionStatus.FAILED || collects.status == TaskExecutionStatus.FAILED) {
            return TaskExecutionStatus.PARTIAL_SUCCESS
        }
        return TaskExecutionStatus.SUCCESS
    }
}
