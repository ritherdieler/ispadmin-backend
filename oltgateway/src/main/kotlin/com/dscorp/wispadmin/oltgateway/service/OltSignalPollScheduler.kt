package com.dscorp.wispadmin.oltgateway.service

import com.dscorp.wispadmin.oltgateway.ssh.LocalCliBusPressure
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
                "SNMP_OPTICAL_POLL_SUMMARY slots={} portsOk={} portsFailed={} rowsMatched={} onusUpdated={} polledAtRefreshed={} incompleteDiscarded={} unchangedSkipped={} unmatchedRows={} durationMs={} localCliBus=true localQueueDepth={} localBusyJobType={} sshActive={} sshMax={}",
                result.slotsPolled,
                (result.portsPolled - result.portsFailed).coerceAtLeast(0),
                result.portsFailed,
                result.rowsMatched,
                result.onusUpdated,
                result.polledAtRefreshed,
                result.incompleteDiscarded,
                result.unchangedSkipped,
                result.unmatchedRows,
                result.durationMs,
                result.localQueueDepth,
                result.localBusyJobType,
                LocalCliBusPressure.SSH_ACTIVE_NA,
                LocalCliBusPressure.SSH_MAX_NA
            )
        }
    }
}
