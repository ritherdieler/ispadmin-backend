package com.dscorp.wispadmin.oltgateway.service

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled

class LabOpticalSshScheduler(
    private val pollService: LabOpticalSshPollService,
) {
    private val logger = LoggerFactory.getLogger(LabOpticalSshScheduler::class.java)

    @Scheduled(
        fixedDelayString = "\${olt.gateway.sync.lab-optical-ssh-interval-ms:900000}",
        initialDelayString = "\${olt.gateway.sync.lab-optical-ssh-initial-delay-ms:120000}",
    )
    fun scheduledLabOpticalPoll() {
        val result = pollService.pollAllLab()
        if (result.error != null) {
            logger.info("Lab optical SSH skipped: {}", result.error)
        } else {
            logger.info("Lab optical SSH collected={} unmapped={}", result.collected, result.unmapped)
        }
    }
}
