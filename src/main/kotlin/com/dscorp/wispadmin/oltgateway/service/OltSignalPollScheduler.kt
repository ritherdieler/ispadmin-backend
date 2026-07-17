package com.dscorp.wispadmin.oltgateway.service

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled

class OltSignalPollScheduler(
    private val signalPollService: OltSignalPollService
) {

    companion object {
        private val logger = LoggerFactory.getLogger(OltSignalPollScheduler::class.java)
    }

    @Scheduled(
        fixedDelayString = "\${olt.gateway.sync.signal-interval-ms:600000}",
        initialDelayString = "\${olt.gateway.sync.signal-initial-delay-ms:90000}"
    )
    fun scheduledSignalPoll() {
        val result = signalPollService.pollSignals()
        if (result.skippedReason != null) {
            logger.info("Signal poll skipped: {}", result.skippedReason)
        } else if (result.error != null) {
            logger.warn("Signal poll error: {}", result.error)
        } else {
            logger.info(
                "Signal poll done slots={} ports={} onusUpdated={} durationMs={}",
                result.slotsPolled,
                result.portsPolled,
                result.onusUpdated,
                result.durationMs
            )
        }
    }
}
