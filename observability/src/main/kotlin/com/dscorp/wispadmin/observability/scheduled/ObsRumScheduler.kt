package com.dscorp.wispadmin.observability.scheduled

import com.dscorp.wispadmin.observability.service.ObsRumCollector
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class ObsRumScheduler(
    private val rumCollector: ObsRumCollector
) {

    private val log = LoggerFactory.getLogger(this::class.java)

    @Scheduled(fixedRate = 60000)
    fun flushMetrics() {
        try {
            rumCollector.flush()
        } catch (e: Exception) {
            log.warn("Error al vaciar metricas RUM: {}", e.message)
        }
    }
}
