package com.dscorp.wispadmin.wispadmin.scheduled

import com.dscorp.wispadmin.wispadmin.config.WhatsAppProperties
import com.dscorp.wispadmin.wispadmin.service.whatsapp.WhatsAppMediaRetentionService
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(prefix = "whatsapp.retention", name = ["enabled"], havingValue = "true", matchIfMissing = true)
class WhatsAppMediaRetentionScheduler(
    private val retentionService: WhatsAppMediaRetentionService,
    private val whatsAppProperties: WhatsAppProperties,
) {

    private val log = LoggerFactory.getLogger(WhatsAppMediaRetentionScheduler::class.java)

    @Scheduled(cron = "0 30 3 * * *", zone = "America/Lima")
    fun purgeExpiredMedia() {
        if (!whatsAppProperties.retention.enabled) {
            return
        }
        try {
            retentionService.purgeExpiredMedia()
        } catch (ex: Exception) {
            log.warn("WhatsApp media retention job failed: {}", ex.message)
        }
    }
}
