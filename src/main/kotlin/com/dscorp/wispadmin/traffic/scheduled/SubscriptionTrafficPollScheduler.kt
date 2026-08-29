package com.dscorp.wispadmin.traffic.scheduled

import com.dscorp.wispadmin.traffic.service.SubscriptionTrafficPollService
import com.dscorp.wispadmin.traffic.service.TrafficAggregationJobService
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(prefix = "traffic.poll", name = ["enabled"], havingValue = "true", matchIfMissing = true)
class SubscriptionTrafficPollScheduler(
    private val pollService: SubscriptionTrafficPollService,
    private val aggregationJobService: TrafficAggregationJobService
) {
    companion object {
        private val logger = LoggerFactory.getLogger(SubscriptionTrafficPollScheduler::class.java)
    }

    @Scheduled(
        fixedDelayString = "\${traffic.poll.interval-ms:60000}",
        initialDelayString = "\${traffic.poll.initial-delay-ms:30000}"
    )
    fun scheduledPoll() {
        val result = pollService.pollTraffic()
        if (result.skippedReason != null) {
            logger.info("Traffic poll skipped: {}", result.skippedReason)
        } else if (result.error != null) {
            logger.warn(
                "Traffic poll partial error devices={} samples={} error={}",
                result.devicesPolled,
                result.samplesWritten,
                result.error
            )
        } else {
            logger.info(
                "Traffic poll done devices={} matched={} samples={} durationMs={}",
                result.devicesPolled,
                result.subscriptionsMatched,
                result.samplesWritten,
                result.durationMs
            )
        }
        if (result.skippedReason == null) {
            try {
                aggregationJobService.catchUpFiveMinute()
            } catch (ex: Exception) {
                logger.warn("Post-poll five-minute catch-up failed: {}", ex.message)
            }
        }
    }
}
