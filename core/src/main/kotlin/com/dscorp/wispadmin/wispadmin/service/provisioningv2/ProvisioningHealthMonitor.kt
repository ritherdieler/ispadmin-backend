package com.dscorp.wispadmin.wispadmin.service.provisioningv2

import com.dscorp.wispadmin.wispadmin.observability.ObservabilityReporter
import com.dscorp.wispadmin.wispadmin.observability.ReportedEvent
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicLong

class ProvisioningWorkerHeartbeat {
    private val lastBeatEpochMs = AtomicLong(0)

    fun beat(at: Instant) = lastBeatEpochMs.set(at.toEpochMilli())

    fun lastBeat(): Instant? = lastBeatEpochMs.get().takeIf { it > 0 }?.let(Instant::ofEpochMilli)
}

data class ProvisioningHealth(
    val activeOperations: Int,
    val stalledOperations: Int,
    val longWaitingOperations: Int,
    val failedLastHour: Int,
    val undeliveredEvents: Long,
    val oldestUndeliveredAgeSeconds: Long?,
    val workerStale: Boolean,
) {
    fun hasAnomaly(): Boolean = stalledOperations > 0 || longWaitingOperations > 0 || failedLastHour > 0 ||
        (oldestUndeliveredAgeSeconds ?: 0) > OUTBOX_BACKLOG_LIMIT.seconds || workerStale

    companion object {
        val OUTBOX_BACKLOG_LIMIT: Duration = Duration.ofMinutes(10)
    }
}

class ProvisioningHealthMonitor(
    private val journal: ProvisioningJournal,
    private val environment: String,
    private val heartbeat: ProvisioningWorkerHeartbeat,
    private val reporter: ObservabilityReporter?,
    private val clock: Clock,
    private val workerEnabled: Boolean,
    private val telemetryEnabled: Boolean = false,
) {
    fun snapshot(): ProvisioningHealth {
        val now = clock.instant()
        val active = journal.operationsInStates(environment, ACTIVE_STATES)
        val failed = journal.operationsInStates(environment, setOf(ProvisioningState.FAILED))
        val (undelivered, oldest) = if (telemetryEnabled) journal.undeliveredEventBacklog() else 0L to null
        return ProvisioningHealth(
            activeOperations = active.size,
            stalledOperations = active.count { it.updatedAt != Instant.EPOCH && it.updatedAt.isBefore(now.minus(STALL_LIMIT)) },
            longWaitingOperations = active.count { operation ->
                operation.checkpoints.any { checkpoint ->
                    val since = checkpoint.waitingSinceEpochMs ?: return@any false
                    now.toEpochMilli() - since > LONG_WAIT_LIMIT.toMillis()
                }
            },
            failedLastHour = failed.count { it.updatedAt.isAfter(now.minus(Duration.ofHours(1))) },
            undeliveredEvents = undelivered,
            oldestUndeliveredAgeSeconds = oldest?.let { (now.toEpochMilli() - it) / 1_000 },
            workerStale = workerEnabled && heartbeat.lastBeat()?.isBefore(now.minus(HEARTBEAT_LIMIT)) != false,
        )
    }

    fun evaluate() {
        val health = runCatching { snapshot() }
            .onFailure { logger.warn("No se pudo calcular la salud del aprovisionamiento", it) }
            .getOrNull() ?: return
        if (!health.hasAnomaly()) return
        logger.warn("provisioning.health {}", health)
        reporter?.report(
            ReportedEvent(
                eventType = "log",
                platform = "backend",
                severity = "warning",
                message = "provisioning.health",
                errorType = null,
                stacktrace = null,
                environment = environment,
                tags = mapOf(
                    "feature" to "fiber-onboarding",
                    "activeOperations" to health.activeOperations,
                    "stalledOperations" to health.stalledOperations,
                    "longWaitingOperations" to health.longWaitingOperations,
                    "failedLastHour" to health.failedLastHour,
                    "undeliveredEvents" to health.undeliveredEvents,
                    "oldestUndeliveredAgeSeconds" to health.oldestUndeliveredAgeSeconds,
                    "workerStale" to health.workerStale,
                ),
            )
        )
    }

    companion object {
        val STALL_LIMIT: Duration = Duration.ofMinutes(10)
        val LONG_WAIT_LIMIT: Duration = Duration.ofMinutes(15)
        val HEARTBEAT_LIMIT: Duration = Duration.ofMinutes(2)
        private val ACTIVE_STATES = setOf(ProvisioningState.PENDING, ProvisioningState.RUNNING, ProvisioningState.WAITING)
        private val logger = LoggerFactory.getLogger(ProvisioningHealthMonitor::class.java)
    }
}
