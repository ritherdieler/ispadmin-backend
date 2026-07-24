package com.dscorp.wispadmin.observability.scheduled

import com.dscorp.wispadmin.observability.config.ObservabilityProperties
import com.dscorp.wispadmin.observability.entity.ObsIssueStatus
import com.dscorp.wispadmin.observability.repository.ObsAlertEventRepository
import com.dscorp.wispadmin.observability.repository.ObsEndpointMetricRepository
import com.dscorp.wispadmin.observability.repository.ObsEventRepository
import com.dscorp.wispadmin.observability.repository.ObsIssueRepository
import com.dscorp.wispadmin.observability.repository.ObsReplayRepository
import com.dscorp.wispadmin.observability.repository.ObsRumMetricRepository
import com.dscorp.wispadmin.observability.repository.ObsSpanRepository
import com.dscorp.wispadmin.observability.repository.ObsSymbolArtifactRepository
import com.dscorp.wispadmin.observability.repository.ObsSystemMetricRepository
import com.dscorp.wispadmin.observability.service.ObsReplayService
import com.dscorp.wispadmin.observability.service.ObsSymbolArtifactService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.time.ZoneId

@Component
class ObsRetentionScheduler(
    private val eventRepository: ObsEventRepository,
    private val replayRepository: ObsReplayRepository,
    private val issueRepository: ObsIssueRepository,
    private val metricRepository: ObsEndpointMetricRepository,
    private val rumMetricRepository: ObsRumMetricRepository,
    private val spanRepository: ObsSpanRepository,
    private val alertEventRepository: ObsAlertEventRepository,
    private val symbolArtifactRepository: ObsSymbolArtifactRepository,
    private val systemMetricRepository: ObsSystemMetricRepository,
    private val replayService: ObsReplayService,
    private val symbolArtifactService: ObsSymbolArtifactService,
    private val properties: ObservabilityProperties
) {

    private val log = LoggerFactory.getLogger(this::class.java)

    @Scheduled(cron = "0 30 3 * * *", zone = "America/Lima")
    @Transactional
    fun purgeExpiredData() {
        val now = LocalDateTime.now()
        val retention = properties.retention
        try {
            val expiredReplays = replayRepository.findByCreatedAtBefore(now.minusDays(retention.replayDays))
            expiredReplays.forEach { replayService.deleteFile(it) }
            if (expiredReplays.isNotEmpty()) replayRepository.deleteAll(expiredReplays)

            val deletedEvents = eventRepository.deleteOlderThan(now.minusDays(retention.eventDays))
            val deletedMetrics = metricRepository.deleteOlderThan(now.minusDays(retention.metricDays))
            val deletedRumMetrics = rumMetricRepository.deleteOlderThan(now.minusDays(retention.rumMetricDays))
            val deletedIssues = issueRepository.deleteByStatusInAndLastSeenBefore(
                listOf(ObsIssueStatus.RESOLVED, ObsIssueStatus.IGNORED),
                now.minusDays(retention.resolvedIssueDays)
            )
            val spanThreshold = now.minusDays(retention.spanDays)
                .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val deletedSpans = spanRepository.deleteOlderThan(spanThreshold)

            val deletedAlertEvents = alertEventRepository.deleteOlderThan(now.minusDays(retention.eventDays))

            val expiredSymbols = symbolArtifactRepository.findByUploadedAtBefore(now.minusDays(retention.symbolDays))
            expiredSymbols.forEach { symbolArtifactService.deleteFile(it) }
            if (expiredSymbols.isNotEmpty()) symbolArtifactRepository.deleteAll(expiredSymbols)

            val deletedSystemMetrics = systemMetricRepository.deleteOlderThan(now.minusDays(retention.systemMetricDays))

            log.info(
                "Retencion observabilidad: eventos={}, replays={}, metricas={}, rum={}, issues={}, spans={}, alertas={}, simbolos={}, sysmetrics={}",
                deletedEvents, expiredReplays.size, deletedMetrics, deletedRumMetrics, deletedIssues, deletedSpans,
                deletedAlertEvents, expiredSymbols.size, deletedSystemMetrics
            )
        } catch (e: Exception) {
            log.warn("Error en la tarea de retencion de observabilidad: {}", e.message)
        }
    }
}
