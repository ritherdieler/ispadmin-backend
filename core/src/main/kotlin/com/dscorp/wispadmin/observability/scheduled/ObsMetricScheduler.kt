package com.dscorp.wispadmin.observability.scheduled

import com.dscorp.wispadmin.observability.service.ObsMetricCollector
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class ObsMetricScheduler(
    private val metricCollector: ObsMetricCollector
) {

    private val log = LoggerFactory.getLogger(this::class.java)

    @Scheduled(fixedRate = 60000)
    fun flushMetrics() {
        try {
            metricCollector.flush()
        } catch (e: Exception) {
            log.warn("Error al vaciar metricas APM: {}", e.message)
        }
    }
}
