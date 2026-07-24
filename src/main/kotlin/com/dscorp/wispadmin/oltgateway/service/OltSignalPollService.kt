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
import java.util.concurrent.atomic.AtomicReference

open class OltSignalPollService(
    oltRepository: OltMgrOltRepository,
    onuRepository: OltMgrOnuRepository,
    statusRepository: OltMgrOnuStatusCurrentRepository,
    taskRepository: OltMgrTaskRepository,
    boardParser: BoardParser,
    opticalInfoParser: OpticalInfoParser,
    signalCategoryCalculator: SignalCategoryCalculator,
    properties: OltGatewayProperties,
    cliBus: OltCliBus? = null
) {

    companion object {
        private val logger = LoggerFactory.getLogger(OltSignalPollService::class.java)
        private val running = AtomicBoolean(false)
        private val lastStartedAtRef = AtomicReference<Instant?>(null)
        private val lastResultRef = AtomicReference<SignalPollResult?>(null)
        private val propertiesRef = AtomicReference<OltGatewayProperties?>(null)
        private val oltRepositoryRef = AtomicReference<OltMgrOltRepository?>(null)
        private val onuRepositoryRef = AtomicReference<OltMgrOnuRepository?>(null)
        private val statusRepositoryRef = AtomicReference<OltMgrOnuStatusCurrentRepository?>(null)
        private val taskRepositoryRef = AtomicReference<OltMgrTaskRepository?>(null)
        private val boardParserRef = AtomicReference<BoardParser?>(null)
        private val opticalInfoParserRef = AtomicReference<OpticalInfoParser?>(null)
        private val signalCategoryCalculatorRef = AtomicReference<SignalCategoryCalculator?>(null)
        private val cliBusRef = AtomicReference<OltCliBus?>(null)
    }

    init {
        propertiesRef.set(properties)
        oltRepositoryRef.set(oltRepository)
        onuRepositoryRef.set(onuRepository)
        statusRepositoryRef.set(statusRepository)
        taskRepositoryRef.set(taskRepository)
        boardParserRef.set(boardParser)
        opticalInfoParserRef.set(opticalInfoParser)
        signalCategoryCalculatorRef.set(signalCategoryCalculator)
        cliBusRef.set(cliBus)
    }

    private fun props(): OltGatewayProperties {
        return propertiesRef.get()
            ?: error("OltGatewayProperties unavailable")
    }

    private fun oltRepository(): OltMgrOltRepository =
        oltRepositoryRef.get() ?: error("OltMgrOltRepository unavailable")

    private fun onuRepository(): OltMgrOnuRepository =
        onuRepositoryRef.get() ?: error("OltMgrOnuRepository unavailable")

    private fun statusRepository(): OltMgrOnuStatusCurrentRepository =
        statusRepositoryRef.get() ?: error("OltMgrOnuStatusCurrentRepository unavailable")

    private fun taskRepository(): OltMgrTaskRepository =
        taskRepositoryRef.get() ?: error("OltMgrTaskRepository unavailable")

    private fun boardParser(): BoardParser =
        boardParserRef.get() ?: error("BoardParser unavailable")

    private fun opticalInfoParser(): OpticalInfoParser =
        opticalInfoParserRef.get() ?: error("OpticalInfoParser unavailable")

    private fun signalCategoryCalculator(): SignalCategoryCalculator =
        signalCategoryCalculatorRef.get() ?: error("SignalCategoryCalculator unavailable")

    private fun cliBus(): OltCliBus? = cliBusRef.get()

    fun isRunning(): Boolean = running.get()

    fun lastStartedAt(): Instant? = lastStartedAtRef.get()

    fun lastResult(): SignalPollResult? = lastResultRef.get()

    fun status(): SignalPollStatusDto {
        return SignalPollStatusDto(
            running = running.get(),
            lastStartedAt = lastStartedAtRef.get()?.toString(),
            lastResult = lastResultRef.get()?.toDto(),
            busQueueDepth = cliBus()?.queueDepth() ?: 0,
            busBusyJobType = cliBus()?.busyJobType()?.name
        )
    }

    open fun pollSignals(): SignalPollResult {
        if (!running.compareAndSet(false, true)) {
            return SignalPollResult(skippedReason = "sync_already_running").also { lastResultRef.set(it) }
        }
        val startedAt = Instant.now()
        lastStartedAtRef.set(startedAt)
        try {
            val bus = cliBus()
            if (bus == null) {
                return finish(SignalPollResult(skippedReason = "cli_bus_unavailable"), startedAt)
            }
            val properties = props()
            if (properties.sync.skipWhenWriteRunning && taskRepository().existsByStatus("running")) {
                return finish(SignalPollResult(skippedReason = "write_task_running"), startedAt)
            }
            val olt = oltRepository().findByName(properties.oltId)
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
            enterGponInterface(session, slotInfo.slot)
            for (port in 0 until slotInfo.portCount) {
                try {
                    rows += pollPortOptical(session, slotInfo.slot, port)
                } catch (ex: Exception) {
                    logger.warn(
                        "Signal poll failed slot={} port={}: {}",
                        slotInfo.slot,
                        port,
                        ex.message
                    )
                    enterGponInterface(session, slotInfo.slot)
                }
                portsPolled++
            }
            try {
                session.execute("quit")
            } catch (ex: Exception) {
                logger.warn("Signal poll quit failed slot={}: {}", slotInfo.slot, ex.message)
            }
        }
        return PollCapture(
            slotsPolled = topology.size,
            portsPolled = portsPolled,
            rows = rows
        )
    }

    private fun pollPortOptical(
        session: HuaweiCliSession,
        slot: Int,
        port: Int
    ): List<OpticalRow> {
        val firstAttempt = pollPortOpticalBulk(session, slot, port, reenter = false)
        if (firstAttempt.isNotEmpty()) {
            return firstAttempt
        }
        return pollPortOpticalBulk(session, slot, port, reenter = true)
    }

    private fun pollPortOpticalBulk(
        session: HuaweiCliSession,
        slot: Int,
        port: Int,
        reenter: Boolean
    ): List<OpticalRow> {
        return try {
            if (reenter) {
                enterGponInterface(session, slot)
            }
            val output = session.execute("display ont optical-info $port all")
            val parsed = opticalInfoParser().parseAll(output)
            if (parsed.isEmpty()) {
                logger.warn(
                    "Signal poll empty bulk parse slot={} port={} outputChars={} reenter={}",
                    slot,
                    port,
                    output.length,
                    reenter
                )
            }
            parsed.map { OpticalRow(slot = slot, port = port, optical = it) }
        } catch (ex: Exception) {
            logger.warn(
                "Signal poll bulk failed slot={} port={} reenter={}: {}",
                slot,
                port,
                reenter,
                ex.message
            )
            emptyList()
        }
    }

    private fun enterGponInterface(session: HuaweiCliSession, slot: Int) {
        session.execute("interface gpon 0/$slot")
    }

    private fun discoverTopology(session: HuaweiCliSession): List<GponSlotInfo> {
        val properties = props()
        val model = oltRepository().findByName(properties.oltId).map { it.model }.orElse(null)
        val maxSlotProbe = model?.maxSlotProbe ?: properties.inventory.maxSlotProbe
        val defaultPorts = model?.defaultPortsPerGponBoard ?: properties.inventory.defaultPortsPerGponBoard
        val discovery = OltGponTopologyDiscovery(boardParser()) { command -> session.execute(command) }
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
        val onus = onuRepository().findByOlt_IdWithStatus(oltId).filter { it.deletedAt == null }
        val byKey = onus.associateBy { Triple(it.board, it.port, it.onuIndex) }
        val now = Instant.now()
        val pendingStatuses = linkedSetOf<OltMgrOnuStatusCurrent>()
        var updated = 0
        for (row in rows) {
            val onu = byKey[Triple(row.slot, row.port, row.optical.ontId)] ?: continue
            if (upsertOptical(onu, row.optical, now, pendingStatuses)) {
                updated++
            }
        }
        if (pendingStatuses.isNotEmpty()) {
            statusRepository().saveAll(pendingStatuses)
        }
        return updated
    }

    private fun upsertOptical(
        onu: OltMgrOnu,
        optical: ParsedOpticalInfo,
        now: Instant,
        pending: MutableSet<OltMgrOnuStatusCurrent>
    ): Boolean {
        val category = signalCategoryCalculator().fromOnuRxDbm(optical.rxPowerDbm)?.value
        val onuRx = toDecimal(optical.rxPowerDbm)
        val onuTx = toDecimal(optical.txPowerDbm)
        val oltRx = toDecimal(optical.oltRxPowerDbm)
        val temperature = optical.temperatureC?.toInt()
        val status = onu.status
        if (status != null) {
            if (!opticalChanged(status, onuRx, onuTx, oltRx, temperature, category)) {
                return false
            }
            status.onuRxDbm = onuRx
            status.onuTxDbm = onuTx
            status.oltRxDbm = oltRx
            status.temperatureC = temperature
            status.signalCategory = category
            status.polledAt = now
            pending += status
            return true
        }
        val created = OltMgrOnuStatusCurrent(
            onu = onu,
            runState = "offline",
            onuRxDbm = onuRx,
            onuTxDbm = onuTx,
            oltRxDbm = oltRx,
            temperatureC = temperature,
            signalCategory = category,
            polledAt = now
        )
        onu.status = created
        pending += created
        return true
    }

    private fun opticalChanged(
        status: OltMgrOnuStatusCurrent,
        onuRx: BigDecimal?,
        onuTx: BigDecimal?,
        oltRx: BigDecimal?,
        temperature: Int?,
        category: String?
    ): Boolean {
        return !decimalsEqual(status.onuRxDbm, onuRx) ||
            !decimalsEqual(status.onuTxDbm, onuTx) ||
            !decimalsEqual(status.oltRxDbm, oltRx) ||
            status.temperatureC != temperature ||
            status.signalCategory != category
    }

    private fun decimalsEqual(left: BigDecimal?, right: BigDecimal?): Boolean {
        if (left == null && right == null) return true
        if (left == null || right == null) return false
        return left.compareTo(right) == 0
    }

    private fun toDecimal(value: Double?): BigDecimal? {
        if (value == null) return null
        return BigDecimal(value.toString()).setScale(2, RoundingMode.HALF_UP)
    }

    private fun finish(result: SignalPollResult, startedAt: Instant): SignalPollResult {
        val finished = Instant.now()
        val withDuration = result.copy(durationMs = finished.toEpochMilli() - startedAt.toEpochMilli())
        lastResultRef.set(withDuration)
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
