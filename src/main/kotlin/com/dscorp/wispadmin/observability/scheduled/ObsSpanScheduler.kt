package com.dscorp.wispadmin.observability.scheduled

import com.dscorp.wispadmin.observability.service.ObsSpanCollector
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class ObsSpanScheduler(
    private val spanCollector: ObsSpanCollector
) {

    private val log = LoggerFactory.getLogger(this::class.java)

    @Scheduled(fixedRate = 5000)
    fun flushSpans() {
        try {
            spanCollector.flush()
        } catch (e: Exception) {
            log.warn("Error al vaciar spans de trazas: {}", e.message)
        }
    }
}
