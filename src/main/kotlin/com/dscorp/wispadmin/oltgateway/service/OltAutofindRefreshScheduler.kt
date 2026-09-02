package com.dscorp.wispadmin.oltgateway.service

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled

open class OltAutofindRefreshScheduler(
    private val cacheService: OltAutofindCacheService
) {

    companion object {
        private val logger = LoggerFactory.getLogger(OltAutofindRefreshScheduler::class.java)
    }

    @Scheduled(
        fixedDelayString = "\${olt.gateway.autofind.refresh-interval-ms:30000}",
        initialDelayString = "\${olt.gateway.autofind.initial-delay-ms:20000}"
    )
    open fun scheduledRefresh() {
        try {
            cacheService.refresh()
        } catch (ex: Exception) {
            logger.warn("Autofind scheduled refresh failed: {}", ex.message)
        }
    }
}
