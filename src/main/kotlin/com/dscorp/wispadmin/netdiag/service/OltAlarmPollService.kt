package com.dscorp.wispadmin.netdiag.service

import com.dscorp.wispadmin.netdiag.domain.repository.NetDiagTargetRepository
import com.dscorp.wispadmin.oltgateway.config.OltGatewayProperties
import com.dscorp.wispadmin.oltgateway.ssh.CliBusResult
import com.dscorp.wispadmin.oltgateway.ssh.CliJobType
import com.dscorp.wispadmin.oltgateway.ssh.OltCliBus
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

data class OltAlarmPollResult(
    val alarmsPersisted: Int = 0,
    val alertsEmitted: Int = 0,
    val cleared: Int = 0,
    val openedIncidentIds: List<Long> = emptyList(),
    val durationMs: Long = 0,
    val skippedReason: String? = null,
    val error: String? = null
)

@Service
@ConditionalOnProperty(prefix = "net.diag", name = ["enabled"], havingValue = "true")
class OltAlarmPollService(
    private val cliBusProvider: ObjectProvider<OltCliBus>,
    private val ingestService: OltAlarmIngestService,
    private val targetRepository: NetDiagTargetRepository,
    private val oltGatewayProperties: OltGatewayProperties
) {

    private val logger = LoggerFactory.getLogger(OltAlarmPollService::class.java)
    private val running = AtomicBoolean(false)
    private val lastResultRef = AtomicReference<OltAlarmPollResult?>(null)
    private val lastStartedAtRef = AtomicReference<Instant?>(null)

    fun status(): OltAlarmPollStatus = OltAlarmPollStatus(
        running = running.get(),
        lastStartedAt = lastStartedAtRef.get(),
        lastResult = lastResultRef.get()
    )

    fun pollActiveAlarms(): OltAlarmPollResult {
        if (!running.compareAndSet(false, true)) {
            return OltAlarmPollResult(skippedReason = "alarm_poll_already_running").also { lastResultRef.set(it) }
        }
        val startedAt = Instant.now()
        lastStartedAtRef.set(startedAt)
        try {
            if (!oltGatewayProperties.sync.alarmEnabled) {
                return finish(OltAlarmPollResult(skippedReason = "alarm_poll_disabled"), startedAt)
            }
            val bus = cliBusProvider.ifAvailable
                ?: return finish(OltAlarmPollResult(skippedReason = "cli_bus_unavailable"), startedAt)
            val oltTargetName = OltNetDiagTargetSyncService.oltTargetName(oltGatewayProperties.oltId)
            val oltTargetId = targetRepository.findByName(oltTargetName).orElse(null)?.id

            val raw = when (val busResult = bus.execute(CliJobType.ALARM_POLL) { session ->
                session.execute("screen-length 0 temporary")
                session.execute("scroll 512")
                session.execute("display alarm active all", ALARM_COMMAND_TIMEOUT_MS)
            }) {
                is CliBusResult.Ok -> busResult.value
                is CliBusResult.Skipped -> return finish(
                    OltAlarmPollResult(skippedReason = busResult.reason),
                    startedAt
                )
            }

            val ingest = ingestService.ingestCliActiveAlarms(
                raw = raw,
                sourceIp = oltGatewayProperties.host,
                oltTargetId = oltTargetId,
                channel = "cli_alarm_active"
            )
            return finish(
                OltAlarmPollResult(
                    alarmsPersisted = ingest.persisted,
                    alertsEmitted = ingest.alertsEmitted,
                    cleared = ingest.cleared,
                    openedIncidentIds = ingest.openedIncidentIds
                ),
                startedAt
            )
        } catch (ex: Exception) {
            logger.warn("OLT alarm poll failed: {}", ex.message)
            return finish(OltAlarmPollResult(error = ex.message), startedAt)
        } finally {
            running.set(false)
        }
    }

    private fun finish(result: OltAlarmPollResult, startedAt: Instant): OltAlarmPollResult {
        val withDuration = result.copy(durationMs = Duration.between(startedAt, Instant.now()).toMillis())
        lastResultRef.set(withDuration)
        return withDuration
    }

    companion object {
        const val ALARM_COMMAND_TIMEOUT_MS: Long = 300_000
    }
}

data class OltAlarmPollStatus(
    val running: Boolean,
    val lastStartedAt: Instant?,
    val lastResult: OltAlarmPollResult?
)
