package com.dscorp.wispadmin.oltgateway.service

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled

class OltInventorySyncScheduler(
    private val syncService: OltInventorySyncService
) {

    companion object {
        private val logger = LoggerFactory.getLogger(OltInventorySyncScheduler::class.java)
    }

    @Scheduled(
        fixedDelayString = "\${olt.gateway.sync.inventory-interval-ms:600000}",
        initialDelayString = "\${olt.gateway.sync.inventory-initial-delay-ms:30000}"
    )
    fun scheduledInventorySync() {
        val result = syncService.syncInventory()
        if (result.skippedReason != null) {
            logger.info("Inventory sync skipped: {}", result.skippedReason)
        } else if (result.error != null) {
            logger.warn("Inventory sync error: {}", result.error)
        } else {
            logger.info(
                "Inventory sync done inserted={} updated={} softDeleted={} unchanged={} durationMs={}",
                result.inserted,
                result.updated,
                result.softDeleted,
                result.unchanged,
                result.durationMs
            )
        }
    }
}
