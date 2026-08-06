package com.dscorp.wispadmin.wispadmin.scheduled

import com.dscorp.wispadmin.wispadmin.service.MonthlyBillingCloseOrchestrator
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class MonthlyBillingCloseScheduler(
    private val monthlyBillingCloseOrchestrator: MonthlyBillingCloseOrchestrator,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "\${dashboard.monthly-close.cron:0 50 23 * * *}", zone = "America/Lima")
    fun runEndOfMonthClose() {
        try {
            val result = monthlyBillingCloseOrchestrator.runMonthlyCloseIfLastDayOfMonth()
            if (result != null) {
                log.info("Monthly billing close finished: {}", result.message)
            }
        } catch (e: Exception) {
            log.error("Monthly billing close scheduler failed: {}", e.message, e)
        }
    }
}
