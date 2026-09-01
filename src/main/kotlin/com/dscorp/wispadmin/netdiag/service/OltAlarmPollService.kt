package com.dscorp.wispadmin.netdiag.service

import com.dscorp.wispadmin.netdiag.domain.repository.NetDiagTargetRepository
import com.dscorp.wispadmin.netdiag.port.NetDiagOltCliPort
import com.dscorp.wispadmin.netdiag.port.NetDiagOltDescriptorPort
import com.dscorp.wispadmin.netdiag.port.OltCliOutcome
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
    private val cliPortProvider: ObjectProvider<NetDiagOltCliPort>,
    private val ingestService: OltAlarmIngestService,
    private val targetRepository: NetDiagTargetRepository,
    private val descriptorProvider: ObjectProvider<NetDiagOltDescriptorPort>
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
            val descriptor = descriptorProvider.ifAvailable?.descriptor()
                ?: return finish(OltAlarmPollResult(skippedReason = "olt_descriptor_unavailable"), startedAt)
            if (!descriptor.alarmPollEnabled) {
                return finish(OltAlarmPollResult(skippedReason = "alarm_poll_disabled"), startedAt)
            }
            val cli = cliPortProvider.ifAvailable
                ?: return finish(OltAlarmPollResult(skippedReason = "cli_bus_unavailable"), startedAt)
            val oltTargetName = OltNetDiagTargetSyncService.oltTargetName(descriptor.oltId)
            val oltTargetId = targetRepository.findByName(oltTargetName).orElse(null)?.id

            val raw = when (val outcome = cli.runAlarmPoll()) {
                is OltCliOutcome.Ok -> outcome.raw
                is OltCliOutcome.Skipped -> return finish(
                    OltAlarmPollResult(skippedReason = outcome.reason),
                    startedAt
                )
            }

            val ingest = ingestService.ingestCliActiveAlarms(
                raw = raw,
                sourceIp = descriptor.host,
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
}

data class OltAlarmPollStatus(
    val running: Boolean,
    val lastStartedAt: Instant?,
    val lastResult: OltAlarmPollResult?
)
