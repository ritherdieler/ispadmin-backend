package com.dscorp.wispadmin.oltgateway.service

import com.dscorp.wispadmin.oltgateway.config.OltGatewayProperties
import com.dscorp.wispadmin.oltgateway.domain.entity.OltMgrOnu
import com.dscorp.wispadmin.oltgateway.domain.entity.OltMgrOnuStatusCurrent
import com.dscorp.wispadmin.oltgateway.domain.repository.OltMgrOltRepository
import com.dscorp.wispadmin.oltgateway.domain.repository.OltMgrOnuRepository
import com.dscorp.wispadmin.oltgateway.domain.repository.OltMgrOnuStatusCurrentRepository
import com.dscorp.wispadmin.oltgateway.domain.repository.OltMgrTaskRepository
import com.dscorp.wispadmin.oltgateway.dto.SignalPollResultDto
import com.dscorp.wispadmin.oltgateway.dto.SignalPollStatusDto
import com.dscorp.wispadmin.oltgateway.parser.BoardParser
import com.dscorp.wispadmin.oltgateway.parser.OpticalInfoParser
import com.dscorp.wispadmin.oltgateway.parser.ParsedOpticalInfo
import com.dscorp.wispadmin.oltgateway.service.inventory.GponSlotInfo
import com.dscorp.wispadmin.oltgateway.service.inventory.OltGponTopologyDiscovery
import com.dscorp.wispadmin.oltgateway.ssh.CliBusResult
import com.dscorp.wispadmin.oltgateway.ssh.CliJobType
import com.dscorp.wispadmin.oltgateway.ssh.HuaweiCliSession
import com.dscorp.wispadmin.oltgateway.ssh.OltCliBus
import org.slf4j.LoggerFactory
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean

open class OltSignalPollService(
    private val oltRepository: OltMgrOltRepository,
    private val onuRepository: OltMgrOnuRepository,
    private val statusRepository: OltMgrOnuStatusCurrentRepository,
    private val taskRepository: OltMgrTaskRepository,
    private val boardParser: BoardParser,
    private val opticalInfoParser: OpticalInfoParser,
    private val signalCategoryCalculator: SignalCategoryCalculator,
    private val properties: OltGatewayProperties,
    private val cliBus: OltCliBus? = null
) {

    companion object {
        private val logger = LoggerFactory.getLogger(OltSignalPollService::class.java)
        private val running = AtomicBoolean(false)
    }

    @Volatile
    private var lastStartedAt: Instant? = null

    @Volatile
    private var lastResult: SignalPollResult? = null

    fun isRunning(): Boolean = running.get()

    fun lastStartedAt(): Instant? = lastStartedAt

    fun lastResult(): SignalPollResult? = lastResult

    fun status(): SignalPollStatusDto {
        return SignalPollStatusDto(
            running = running.get(),
            lastStartedAt = lastStartedAt?.toString(),
            lastResult = lastResult?.toDto(),
            busQueueDepth = cliBus?.queueDepth() ?: 0,
            busBusyJobType = cliBus?.busyJobType()?.name
        )
    }

    open fun pollSignals(): SignalPollResult {
        if (!running.compareAndSet(false, true)) {
            return SignalPollResult(skippedReason = "sync_already_running").also { lastResult = it }
        }
        val startedAt = Instant.now()
        lastStartedAt = startedAt
        try {
            val bus = cliBus
            if (bus == null) {
                return finish(SignalPollResult(skippedReason = "cli_bus_unavailable"), startedAt)
            }
            if (properties.sync.skipWhenWriteRunning && taskRepository.existsByStatus("running")) {
                return finish(SignalPollResult(skippedReason = "write_task_running"), startedAt)
            }
            val olt = oltRepository.findByName(properties.oltId)
                .orElseThrow { IllegalStateException("OLT seed missing for ${properties.oltId}") }

            val capture = when (val busResult = bus.execute(CliJobType.SIGNAL_POLL) { session ->
                pollOnSession(session)
            }) {
                is CliBusResult.Ok -> busResult.value
                is CliBusResult.Skipped -> return finish(
                    SignalPollResult(skippedReason = busResult.reason),
                    startedAt
                )
            }

            val onusUpdated = applyOpticalUpdates(olt.id!!, capture.rows)
            return finish(
                SignalPollResult(
                    slotsPolled = capture.slotsPolled,
                    portsPolled = capture.portsPolled,
                    onusUpdated = onusUpdated
                ),
                startedAt
            )
        } catch (ex: Exception) {
            logger.warn("Signal poll failed: {}", ex.message)
            return finish(SignalPollResult(error = ex.message), startedAt)
        } finally {
            running.set(false)
        }
    }

    private fun pollOnSession(session: HuaweiCliSession): PollCapture {
        val topology = discoverTopology(session)
        val rows = mutableListOf<OpticalRow>()
        var portsPolled = 0
        for (slotInfo in topology) {
            session.execute("interface gpon 0/${slotInfo.slot}")
            for (port in 0 until slotInfo.portCount) {
                try {
                    val output = session.execute("display ont optical-info $port all")
                    val parsed = opticalInfoParser.parseAll(output)
                    for (item in parsed) {
                        rows += OpticalRow(
                            slot = slotInfo.slot,
                            port = port,
                            optical = item
                        )
                    }
                } catch (ex: Exception) {
                    logger.warn(
                        "Signal poll failed slot={} port={}: {}",
                        slotInfo.slot,
                        port,
                        ex.message
                    )
                }
                portsPolled++
            }
            session.execute("quit")
        }
        return PollCapture(
            slotsPolled = topology.size,
            portsPolled = portsPolled,
            rows = rows
        )
    }

    private fun discoverTopology(session: HuaweiCliSession): List<GponSlotInfo> {
        val model = oltRepository.findByName(properties.oltId).map { it.model }.orElse(null)
        val maxSlotProbe = model?.maxSlotProbe ?: properties.inventory.maxSlotProbe
        val defaultPorts = model?.defaultPortsPerGponBoard ?: properties.inventory.defaultPortsPerGponBoard
        val discovery = OltGponTopologyDiscovery(boardParser) { command -> session.execute(command) }
        return discovery.discover(
            maxSlotProbe = maxSlotProbe,
            defaultPortsPerGponBoard = defaultPorts
        )
    }

    @Transactional
    open fun applyOpticalUpdates(oltId: Long, rows: List<OpticalRow>): Int {
        if (rows.isEmpty()) {
            return 0
        }
        val onus = onuRepository.findByOlt_Id(oltId).filter { it.deletedAt == null }
        val byKey = onus.associateBy { Triple(it.board, it.port, it.onuIndex) }
        val now = Instant.now()
        var updated = 0
        for (row in rows) {
            val onu = byKey[Triple(row.slot, row.port, row.optical.ontId)] ?: continue
            if (upsertOptical(onu, row.optical, now)) {
                updated++
            }
        }
        return updated
    }

    private fun upsertOptical(onu: OltMgrOnu, optical: ParsedOpticalInfo, now: Instant): Boolean {
        val category = signalCategoryCalculator.fromOnuRxDbm(optical.rxPowerDbm)?.value
        val existing = statusRepository.findById(onu.id!!)
        if (existing.isPresent) {
            val status = existing.get()
            status.onuRxDbm = toDecimal(optical.rxPowerDbm)
            status.onuTxDbm = toDecimal(optical.txPowerDbm)
            status.oltRxDbm = toDecimal(optical.oltRxPowerDbm)
            status.temperatureC = optical.temperatureC?.toInt()
            status.signalCategory = category
            status.polledAt = now
            statusRepository.save(status)
            return true
        }
        val status = OltMgrOnuStatusCurrent(
            onu = onu,
            runState = "offline",
            onuRxDbm = toDecimal(optical.rxPowerDbm),
            onuTxDbm = toDecimal(optical.txPowerDbm),
            oltRxDbm = toDecimal(optical.oltRxPowerDbm),
            temperatureC = optical.temperatureC?.toInt(),
            signalCategory = category,
            polledAt = now
        )
        onu.status = status
        statusRepository.save(status)
        return true
    }

    private fun toDecimal(value: Double?): BigDecimal? {
        if (value == null) return null
        return BigDecimal(value.toString()).setScale(2, RoundingMode.HALF_UP)
    }

    private fun finish(result: SignalPollResult, startedAt: Instant): SignalPollResult {
        val finished = Instant.now()
        val withDuration = result.copy(durationMs = finished.toEpochMilli() - startedAt.toEpochMilli())
        lastResult = withDuration
        return withDuration
    }

    private fun SignalPollResult.toDto(): SignalPollResultDto {
        return SignalPollResultDto(
            slotsPolled = slotsPolled,
            portsPolled = portsPolled,
            onusUpdated = onusUpdated,
            durationMs = durationMs,
            skippedReason = skippedReason,
            error = error
        )
    }

    data class OpticalRow(
        val slot: Int,
        val port: Int,
        val optical: ParsedOpticalInfo
    )

    data class PollCapture(
        val slotsPolled: Int,
        val portsPolled: Int,
        val rows: List<OpticalRow>
    )
}
