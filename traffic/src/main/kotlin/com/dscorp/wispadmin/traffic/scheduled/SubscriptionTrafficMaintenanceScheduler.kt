package com.dscorp.wispadmin.traffic.scheduled

import com.dscorp.wispadmin.traffic.service.NetworkTrafficRollupService
import com.dscorp.wispadmin.traffic.service.SubscriptionTrafficRetentionService
import com.dscorp.wispadmin.traffic.service.TrafficAggregationJobService
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(prefix = "traffic.poll", name = ["enabled"], havingValue = "true", matchIfMissing = true)
class SubscriptionTrafficAggregationScheduler(
    private val aggregationJobService: TrafficAggregationJobService
) {
    companion object {
        private val logger = LoggerFactory.getLogger(SubscriptionTrafficAggregationScheduler::class.java)
    }

    @Scheduled(fixedDelayString = "300000", initialDelayString = "60000")
    fun catchUpFiveMinuteAndHourly() {
        try {
            aggregationJobService.catchUpFiveMinute()
            aggregationJobService.catchUpHourly()
        } catch (ex: Exception) {
            logger.warn("Traffic aggregation catch-up failed: {}", ex.message)
        }
    }

    @Scheduled(cron = "5 0 * * * *", zone = "America/Lima")
    fun catchUpClosedHour() {
        try {
            aggregationJobService.catchUpHourly()
        } catch (ex: Exception) {
            logger.warn("Hourly catch-up failed: {}", ex.message)
        }
    }
}

@Component
@ConditionalOnProperty(prefix = "traffic.poll", name = ["enabled"], havingValue = "true", matchIfMissing = true)
class SubscriptionTrafficMaintenanceScheduler(
    private val aggregationJobService: TrafficAggregationJobService,
    private val networkRollupService: NetworkTrafficRollupService,
    private val retentionService: SubscriptionTrafficRetentionService
) {
    companion object {
        private val logger = LoggerFactory.getLogger(SubscriptionTrafficMaintenanceScheduler::class.java)
    }

    @Scheduled(cron = "0 30 3 * * *", zone = "America/Lima")
    fun nightlyMaintenance() {
        try {
            aggregationJobService.catchUpDaily()
            networkRollupService.rollupRecentDays(7)
            retentionService.purgeExpired()
            logger.info("Traffic maintenance done rollup days=7")
        } catch (ex: Exception) {
            logger.warn("Traffic maintenance failed: {}", ex.message)
        }
    }
}
