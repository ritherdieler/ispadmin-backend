package com.dscorp.wispadmin.netdiag.service

import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
@ConditionalOnProperty(prefix = "net.diag", name = ["enabled"], havingValue = "true")
class NetDiagPollScheduler(
    private val pollService: NetDiagPollService
) {

    private val logger = LoggerFactory.getLogger(NetDiagPollScheduler::class.java)

    @Scheduled(
        fixedDelayString = "\${net.diag.poll.interval-ms:60000}",
        initialDelayString = "\${net.diag.poll.initial-delay-ms:15000}"
    )
    fun scheduledPoll() {
        try {
            pollService.pollAllEnabledTargets()
        } catch (ex: Exception) {
            logger.warn("NetDiag scheduled poll failed: {}", ex.message)
        }
    }

    @Scheduled(cron = "0 30 3 * * *")
    @Transactional
    fun scheduledRetention() {
        try {
            val deleted = pollService.purgeOldProbeRuns()
            if (deleted > 0) {
                logger.info("NetDiag probe_run retention deleted={}", deleted)
            }
        } catch (ex: Exception) {
            logger.warn("NetDiag retention job failed: {}", ex.message)
        }
    }
}
