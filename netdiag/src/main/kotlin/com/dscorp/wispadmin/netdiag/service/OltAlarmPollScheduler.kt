package com.dscorp.wispadmin.netdiag.service

import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(prefix = "net.diag", name = ["enabled"], havingValue = "true")
class OltAlarmPollScheduler(
    private val alarmPollService: OltAlarmPollService
) {

    private val logger = LoggerFactory.getLogger(OltAlarmPollScheduler::class.java)

    @Scheduled(
        fixedDelayString = "\${olt.gateway.sync.alarm-interval-ms:120000}",
        initialDelayString = "\${olt.gateway.sync.alarm-initial-delay-ms:45000}"
    )
    fun scheduledAlarmPoll() {
        val result = alarmPollService.pollActiveAlarms()
        if (result.skippedReason != null) {
            logger.info("OLT alarm poll skipped: {}", result.skippedReason)
        } else if (result.error != null) {
            logger.warn("OLT alarm poll error: {}", result.error)
        } else {
            logger.info(
                "OLT alarm poll done persisted={} alerts={} cleared={} opened={} durationMs={}",
                result.alarmsPersisted,
                result.alertsEmitted,
                result.cleared,
                result.openedIncidentIds.size,
                result.durationMs
            )
        }
    }
}
