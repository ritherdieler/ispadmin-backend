package com.dscorp.wispadmin.traffic.scheduled

import com.dscorp.wispadmin.traffic.service.NetworkTrafficRollupService
import com.dscorp.wispadmin.traffic.service.SubscriptionTrafficRetentionService
import com.dscorp.wispadmin.traffic.service.SubscriptionTrafficRollupService
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(prefix = "traffic.poll", name = ["enabled"], havingValue = "true", matchIfMissing = true)
class SubscriptionTrafficMaintenanceScheduler(
    private val rollupService: SubscriptionTrafficRollupService,
    private val networkRollupService: NetworkTrafficRollupService,
    private val retentionService: SubscriptionTrafficRetentionService
) {
    companion object {
        private val logger = LoggerFactory.getLogger(SubscriptionTrafficMaintenanceScheduler::class.java)
    }

    @Scheduled(cron = "0 30 3 * * *", zone = "America/Lima")
    fun nightlyMaintenance() {
        try {
            rollupService.rollupAll()
            networkRollupService.rollupRecentDays(7)
            val purge = retentionService.purgeExpired()
            logger.info(
                "Traffic maintenance done purge raw={} hourly={} daily={} networkHour={}",
                purge.rawDeleted,
                purge.hourlyDeleted,
                purge.dailyDeleted,
                purge.networkHourDeleted
            )
        } catch (ex: Exception) {
            logger.warn("Traffic maintenance failed: {}", ex.message)
        }
    }
}
