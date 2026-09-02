package com.dscorp.wispadmin.shared.telemetry

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Instant

interface TelemetryRetentionPort {
    fun name(): String
    fun purgeExpired(now: Instant = Instant.now()): Int
}

data class TelemetryRetentionResult(
    val deleted: Int = 0,
    val ran: Int = 0,
    val failed: Int = 0
)

@Component
class TelemetryRetentionCoordinator(
    private val ports: List<TelemetryRetentionPort>
) {

    fun purgeExpired(now: Instant = Instant.now()): TelemetryRetentionResult {
        var deleted = 0
        var ran = 0
        var failed = 0
        ports.forEach { port ->
            try {
                deleted += port.purgeExpired(now)
                ran++
            } catch (ex: Exception) {
                failed++
                logger.warn("Retención de {} falló: {}", port.name(), ex.message)
            }
        }
        return TelemetryRetentionResult(deleted = deleted, ran = ran, failed = failed)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(TelemetryRetentionCoordinator::class.java)
    }
}

@Component
class NetDiagTelemetryRetentionAdapter(
    private val service: ObjectProvider<com.dscorp.wispadmin.netdiag.service.NetDiagRetentionService>
) : TelemetryRetentionPort {
    override fun name() = "netdiag"
    override fun purgeExpired(now: Instant): Int =
        service.ifAvailable?.purgeExpired(now)?.total() ?: 0
}

@Component
class TrafficTelemetryRetentionAdapter(
    private val service: ObjectProvider<com.dscorp.wispadmin.traffic.service.SubscriptionTrafficRetentionService>
) : TelemetryRetentionPort {
    override fun name() = "traffic"
    override fun purgeExpired(now: Instant): Int {
        val result = service.ifAvailable?.purgeExpired() ?: return 0
        return result.rawDeleted + result.fiveMinuteDeleted + result.hourlyDeleted +
            result.dailyDeleted + result.networkHourDeleted
    }
}

@Component
class ObservabilityTelemetryRetentionAdapter(
    private val scheduler: ObjectProvider<com.dscorp.wispadmin.observability.scheduled.ObsRetentionScheduler>
) : TelemetryRetentionPort {
    override fun name() = "observability"
    override fun purgeExpired(now: Instant): Int {
        scheduler.ifAvailable?.purgeExpiredData()
        return 0
    }
}

@Component
class TelemetryRetentionScheduler(
    private val coordinator: TelemetryRetentionCoordinator
) {

    @Scheduled(cron = "\${telemetry.retention.cron:0 20 3 * * *}")
    fun scheduled() {
        val result = coordinator.purgeExpired()
        if (result.deleted > 0 || result.failed > 0) {
            logger.info(
                "Retención de telemetría deleted={} ran={} failed={}",
                result.deleted,
                result.ran,
                result.failed
            )
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(TelemetryRetentionScheduler::class.java)
    }
}
