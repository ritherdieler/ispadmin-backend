package com.dscorp.wispadmin.oltgateway.service

import com.dscorp.wispadmin.events.EventBusPort
import com.dscorp.wispadmin.events.NoOpEventBus
import com.dscorp.wispadmin.events.OnuOpticalBatchItem
import com.dscorp.wispadmin.events.OnuOpticalBatchPayload
import com.dscorp.wispadmin.events.PlatformEvent
import com.dscorp.wispadmin.events.PlatformEventTypes
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
import com.dscorp.wispadmin.oltgateway.snmp.HuaweiGponSnmpCodec
import com.dscorp.wispadmin.oltgateway.snmp.OltSnmpClient
import com.dscorp.wispadmin.oltgateway.snmp.OltSnmpPollLocker
import com.dscorp.wispadmin.oltgateway.snmp.NoOpOltSnmpPollLock
import com.dscorp.wispadmin.oltgateway.snmp.OpticalPollScope
import com.dscorp.wispadmin.oltgateway.snmp.SnmpOntOptical
import com.dscorp.wispadmin.oltgateway.ssh.CliBusResult
import com.dscorp.wispadmin.oltgateway.ssh.CliJobType
import com.dscorp.wispadmin.oltgateway.ssh.HuaweiCliSession
import com.dscorp.wispadmin.oltgateway.ssh.LocalCliBusPressure
import com.dscorp.wispadmin.oltgateway.ssh.OltCliBus
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
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
    cliBus: OltCliBus? = null,
    snmpClient: OltSnmpClient? = null,
    eventPublisher: org.springframework.context.ApplicationEventPublisher? = null,
    private val eventBus: EventBusPort = NoOpEventBus(),
    private val objectMapper: ObjectMapper = ObjectMapper()
        .registerModule(JavaTimeModule())
        .findAndRegisterModules()
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS),
    pollLock: OltSnmpPollLocker = NoOpOltSnmpPollLock(),
) {

    companion object {
        private val telemetryPublisher = AtomicReference<org.springframework.context.ApplicationEventPublisher?>(null)
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
        private val snmpClientRef = AtomicReference<OltSnmpClient?>(null)
        private val pollLockRef = AtomicReference<OltSnmpPollLocker>(NoOpOltSnmpPollLock())
    }

    init {
        telemetryPublisher.set(eventPublisher)
        propertiesRef.set(properties)
        oltRepositoryRef.set(oltRepository)
        onuRepositoryRef.set(onuRepository)
        statusRepositoryRef.set(statusRepository)
        taskRepositoryRef.set(taskRepository)
        boardParserRef.set(boardParser)
        opticalInfoParserRef.set(opticalInfoParser)
        signalCategoryCalculatorRef.set(signalCategoryCalculator)
        cliBusRef.set(cliBus)
        snmpClientRef.set(snmpClient)
        pollLockRef.set(pollLock)
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

    private fun snmpClient(): OltSnmpClient? = snmpClientRef.get()

    private fun pollLock(): OltSnmpPollLocker = pollLockRef.get() ?: NoOpOltSnmpPollLock()

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
        return pollSignalsInternal(source = resolveSignalSource())
    }

    open fun pollSignalsFromSnmp(): SignalPollResult {
        return pollSignalsInternal(source = SignalSource.SNMP)
    }

    private enum class SignalSource { SSH, SNMP }

    /**
     * Signal poll is SNMP-first. SSH optical collection is deprecated for this task.
     */
    private fun resolveSignalSource(): SignalSource {
        val properties = props()
        val snmpReady = properties.snmp.enabled &&
            properties.snmp.roCommunity.isNotBlank() &&
            snmpClient() != null
        if (snmpReady) {
            return SignalSource.SNMP
        }
        if (properties.snmp.allowSshSignalFallback) {
            logger.warn(
                "Signal poll using deprecated SSH optical path " +
                    "(enable olt.gateway.snmp + OLT_GATEWAY_SNMP_RO_COMMUNITY to use SNMP)"
            )
            return SignalSource.SSH
        }
        return SignalSource.SNMP
    }

    private fun pollSignalsInternal(source: SignalSource): SignalPollResult {
        if (!running.compareAndSet(false, true)) {
            return SignalPollResult(skippedReason = "sync_already_running").also { lastResultRef.set(it) }
        }
        val startedAt = Instant.now()
        lastStartedAtRef.set(startedAt)
        val logPressure = source == SignalSource.SNMP
        if (logPressure) {
            logLocalCliBusPressure("start")
        }
        try {
            val properties = props()
            if (properties.sync.skipWhenWriteRunning && taskRepository().existsByStatus("running")) {
                return finish(SignalPollResult(skippedReason = "write_task_running"), startedAt)
            }
            val olt = oltRepository().findByName(properties.oltId)
                .orElseThrow { IllegalStateException("OLT seed missing for ${properties.oltId}") }

            return pollLock().withLock {
                val capture = when (source) {
                    SignalSource.SNMP -> captureViaSnmp(properties, olt.id!!)
                    SignalSource.SSH -> captureViaSshDeprecated()
                } ?: return@withLock finish(SignalPollResult(skippedReason = lastSkipReason), startedAt)

                val apply = applyOpticalUpdatesByPort(olt.id!!, capture.rows)
                finish(
                    SignalPollResult(
                        slotsPolled = capture.slotsPolled,
                        portsPolled = capture.portsPolled,
                        portsFailed = capture.portsFailed,
                        onusUpdated = apply.onusUpdated,
                        polledAtRefreshed = apply.polledAtRefreshed,
                        incompleteDiscarded = apply.incompleteDiscarded,
                        unchangedSkipped = apply.unchangedSkipped,
                        unmatchedRows = apply.unmatchedRows,
                        rowsMatched = apply.rowsMatched,
                    ),
                    startedAt
                )
            }
        } catch (ex: Exception) {
            logger.warn("Signal poll failed (source={}): {}", source, ex.message)
            return finish(SignalPollResult(error = ex.message), startedAt)
        } finally {
            if (logPressure) {
                logLocalCliBusPressure("end")
            }
            running.set(false)
        }
    }

    private fun logLocalCliBusPressure(phase: String) {
        val pressure = LocalCliBusPressure.snapshot(cliBus())
        logger.info(
            "SNMP_OPTICAL_SSH_PRESSURE phase={} localCliBus=true localQueueDepth={} localBusyJobType={} sshActive={} sshMax={}",
            phase,
            pressure.localQueueDepth,
            pressure.localBusyJobType,
            LocalCliBusPressure.SSH_ACTIVE_NA,
            LocalCliBusPressure.SSH_MAX_NA
        )
    }

    @Volatile
    private var lastSkipReason: String? = null

    private fun captureViaSnmp(properties: OltGatewayProperties, oltId: Long): PollCapture? {
        lastSkipReason = null
        val client = snmpClient()
        if (client == null) {
            lastSkipReason = "snmp_client_unavailable"
            return null
        }
        if (!properties.snmp.enabled || properties.snmp.roCommunity.isBlank()) {
            lastSkipReason = "snmp_required"
            return null
        }
        val optical = if (properties.snmp.opticalPerPortWalks) {
            val onus = onuRepository().findByOlt_IdWithStatus(oltId).filter { it.deletedAt == null }
            val ports = OpticalPollScope.portsFromOnus(onus, properties.snmp.opticalOnlineOnly)
            if (ports.isEmpty()) {
                lastSkipReason = "no_onus_to_poll"
                return null
            }
            logger.info(
                "Signal poll via SNMP GETBULK per-port ports={} onlineOnly={} parallelPorts={}",
                ports.size,
                properties.snmp.opticalOnlineOnly,
                properties.snmp.opticalParallelPorts
            )
            client.listOptical(ports)
        } else {
            logger.info(
                "Signal poll via SNMP GETBULK full-table parallelColumns={}",
                properties.snmp.opticalParallelColumns
            )
            client.listOptical(null)
        }
        val rows = optical.map { it.toOpticalRow() }
        val slots = rows.map { it.slot }.toSet().size
        val portsPolled = if (properties.snmp.opticalPerPortWalks) {
            client.lastOpticalWalkPortsAttempted().coerceAtLeast(rows.map { it.slot to it.port }.toSet().size)
        } else {
            rows.map { it.slot to it.port }.toSet().size
        }
        val portsFailed = if (properties.snmp.opticalPerPortWalks) client.lastOpticalWalkPortsFailed() else 0
        return PollCapture(
            slotsPolled = slots,
            portsPolled = portsPolled,
            portsFailed = portsFailed,
            rows = rows,
        )
    }

    private fun SnmpOntOptical.toOpticalRow(): OpticalRow {
        val fsp = HuaweiGponSnmpCodec.decodeIfIndex(key.ifIndex)
        return OpticalRow(
            slot = fsp.slot,
            port = fsp.port,
            optical = ParsedOpticalInfo(
                ontId = key.ontId,
                rxPowerDbm = onuRxDbm,
                txPowerDbm = onuTxDbm,
                oltRxPowerDbm = oltRxDbm,
                temperatureC = temperatureC,
                biasCurrentMa = biasCurrentMa,
                distanceM = distanceM
            )
        )
    }

    /** @deprecated SSH optical poll — only when allowSshSignalFallback=true. */
    @Deprecated("SSH optical signal poll is deprecated; use SNMP listOptical()")
    private fun captureViaSshDeprecated(): PollCapture? {
        lastSkipReason = null
        val bus = cliBus()
        if (bus == null) {
            lastSkipReason = "cli_bus_unavailable"
            return null
        }
        return when (val busResult = bus.execute(CliJobType.SIGNAL_POLL) { session ->
            pollOnSession(session)
        }) {
            is CliBusResult.Ok -> busResult.value
            is CliBusResult.Skipped -> {
                lastSkipReason = busResult.reason
                null
            }
        }
    }

    @Deprecated("SSH optical path")
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
            portsFailed = 0,
            rows = rows
        )
    }

    @Deprecated("SSH optical path")
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

    @Deprecated("SSH optical path")
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

    @Deprecated("SSH optical path")
    private fun enterGponInterface(session: HuaweiCliSession, slot: Int) {
        session.execute("interface gpon 0/$slot")
    }

    @Deprecated("SSH optical path")
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
    open fun applyOpticalUpdatesByPort(oltId: Long, rows: List<OpticalRow>): OpticalApplyStats {
        if (rows.isEmpty()) {
            return OpticalApplyStats()
        }
        var aggregate = OpticalApplyStats()
        val byPort = rows.groupBy { it.slot to it.port }
        for (key in byPort.keys.sortedWith(compareBy({ it.first }, { it.second }))) {
            aggregate = aggregate.plus(applyOpticalUpdates(oltId, byPort.getValue(key)))
        }
        return aggregate
    }

    @Transactional
    open fun applyOpticalUpdates(oltId: Long, rows: List<OpticalRow>): OpticalApplyStats {
        if (rows.isEmpty()) {
            return OpticalApplyStats()
        }
        telemetryPublisher.get()?.publishEvent(OltOpticalObservation(oltId, Instant.now(), rows))
        val onus = onuRepository().findByOlt_IdWithStatus(oltId).filter { it.deletedAt == null }
        val byKey = onus.associateBy { Triple(it.board, it.port, it.onuIndex) }
        val now = Instant.now()
        val pendingStatuses = linkedSetOf<OltMgrOnuStatusCurrent>()
        val batchItems = mutableListOf<OnuOpticalBatchItem>()
        var updated = 0
        var refreshed = 0
        var incomplete = 0
        var unmatched = 0
        var matched = 0
        var slot = rows.first().slot
        var port = rows.first().port
        for (row in rows) {
            slot = row.slot
            port = row.port
            val onu = byKey[Triple(row.slot, row.port, row.optical.ontId)]
            if (onu == null) {
                unmatched++
                continue
            }
            matched++
            when (upsertOptical(onu, row.optical, now, pendingStatuses)) {
                OpticalUpsertOutcome.VALUES_UPDATED -> {
                    updated++
                    val rx = row.optical.rxPowerDbm
                    val tx = row.optical.txPowerDbm
                    val oltRx = row.optical.oltRxPowerDbm
                    if (rx != null && tx != null && oltRx != null) {
                        batchItems += toBatchItem(onu, rx, tx, oltRx, row.optical, now)
                    }
                }
                OpticalUpsertOutcome.POLLED_AT_REFRESHED -> {
                    refreshed++
                    val rx = row.optical.rxPowerDbm
                    val tx = row.optical.txPowerDbm
                    val oltRx = row.optical.oltRxPowerDbm
                    if (rx != null && tx != null && oltRx != null) {
                        batchItems += toBatchItem(onu, rx, tx, oltRx, row.optical, now)
                    }
                }
                OpticalUpsertOutcome.INCOMPLETE_DISCARDED -> incomplete++
            }
        }
        if (pendingStatuses.isNotEmpty()) {
            statusRepository().saveAll(pendingStatuses)
        }
        if (batchItems.isNotEmpty()) {
            publishOpticalBatch(oltId, slot, port, now, batchItems)
        }
        return OpticalApplyStats(
            onusUpdated = updated,
            polledAtRefreshed = refreshed,
            incompleteDiscarded = incomplete,
            unmatchedRows = unmatched,
            rowsMatched = matched,
        )
    }

    private fun toBatchItem(
        onu: OltMgrOnu,
        onuRxDbm: Double,
        onuTxDbm: Double,
        oltRxDbm: Double,
        optical: ParsedOpticalInfo,
        polledAt: Instant,
    ): OnuOpticalBatchItem {
        return OnuOpticalBatchItem(
            sn = onu.sn,
            onuExternalId = onu.externalId,
            onuRxDbm = onuRxDbm,
            onuTxDbm = onuTxDbm,
            oltRxDbm = oltRxDbm,
            polledAt = polledAt,
            temperatureC = optical.temperatureC,
            distanceM = optical.distanceM,
            biasCurrentMa = optical.biasCurrentMa,
            runState = onu.status?.runState,
        )
    }

    private fun publishOpticalBatch(
        oltId: Long,
        slot: Int,
        port: Int,
        polledAt: Instant,
        items: List<OnuOpticalBatchItem>,
    ) {
        val payload = OnuOpticalBatchPayload(
            oltId = oltId,
            slot = slot,
            port = port,
            polledAt = polledAt,
            onus = items,
        )
        eventBus.publish(
            PlatformEvent(
                type = PlatformEventTypes.ONU_OPTICAL_BATCH,
                occurredAt = polledAt,
                payloadJson = objectMapper.writeValueAsString(payload),
                producer = "oltgateway",
            )
        )
    }

    private enum class OpticalUpsertOutcome {
        VALUES_UPDATED,
        POLLED_AT_REFRESHED,
        INCOMPLETE_DISCARDED,
    }

    private fun upsertOptical(
        onu: OltMgrOnu,
        optical: ParsedOpticalInfo,
        now: Instant,
        pending: MutableSet<OltMgrOnuStatusCurrent>
    ): OpticalUpsertOutcome {
        val onuRx = toDecimal(optical.rxPowerDbm)
        val onuTx = toDecimal(optical.txPowerDbm)
        val oltRx = toDecimal(optical.oltRxPowerDbm)
        if (onuRx == null || onuTx == null || oltRx == null) {
            logger.info(
                "SNMP_OPTICAL_INCOMPLETE_DISCARD sn={} slot={} port={} ontId={} rx={} tx={} oltRx={}",
                onu.sn,
                onu.board,
                onu.port,
                optical.ontId,
                optical.rxPowerDbm,
                optical.txPowerDbm,
                optical.oltRxPowerDbm
            )
            return OpticalUpsertOutcome.INCOMPLETE_DISCARDED
        }
        val temperature = optical.temperatureC?.toInt()
        val distanceM = optical.distanceM
        val status = onu.status
        if (status != null) {
            val nextTemp = temperature ?: status.temperatureC
            val nextDistance = distanceM ?: status.distanceM
            val nextCategory = signalCategoryCalculator().fromOnuRxDbm(onuRx.toDouble())?.value
                ?: status.signalCategory
            val valuesChanged = opticalChanged(status, onuRx, onuTx, oltRx, nextTemp, nextDistance, nextCategory)
            status.onuRxDbm = onuRx
            status.onuTxDbm = onuTx
            status.oltRxDbm = oltRx
            status.temperatureC = nextTemp
            status.distanceM = nextDistance
            status.signalCategory = nextCategory
            status.polledAt = now
            pending += status
            return if (valuesChanged) {
                OpticalUpsertOutcome.VALUES_UPDATED
            } else {
                OpticalUpsertOutcome.POLLED_AT_REFRESHED
            }
        }
        val category = signalCategoryCalculator().fromOnuRxDbm(onuRx.toDouble())?.value
        val created = OltMgrOnuStatusCurrent(
            onu = onu,
            runState = "offline",
            onuRxDbm = onuRx,
            onuTxDbm = onuTx,
            oltRxDbm = oltRx,
            temperatureC = temperature,
            distanceM = distanceM,
            signalCategory = category,
            polledAt = now
        )
        onu.status = created
        pending += created
        return OpticalUpsertOutcome.VALUES_UPDATED
    }

    private fun opticalChanged(
        status: OltMgrOnuStatusCurrent,
        onuRx: BigDecimal?,
        onuTx: BigDecimal?,
        oltRx: BigDecimal?,
        temperature: Int?,
        distanceM: Int?,
        category: String?
    ): Boolean {
        return !decimalsEqual(status.onuRxDbm, onuRx) ||
            !decimalsEqual(status.onuTxDbm, onuTx) ||
            !decimalsEqual(status.oltRxDbm, oltRx) ||
            status.temperatureC != temperature ||
            status.distanceM != distanceM ||
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
        val pressure = LocalCliBusPressure.snapshot(cliBus())
        val withDuration = result.copy(
            durationMs = finished.toEpochMilli() - startedAt.toEpochMilli(),
            localQueueDepth = pressure.localQueueDepth,
            localBusyJobType = pressure.localBusyJobType
        )
        lastResultRef.set(withDuration)
        if (telemetryPublisher.get() != null && (result.error != null || result.skippedReason != null)) {
            val oltId = oltRepository().findByName(props().oltId).orElse(null)?.id
            if (oltId != null) telemetryPublisher.get()?.publishEvent(OltOpticalFailure(oltId, finished,
                if (result.error != null) "OPTICAL_POLL_FAILED" else "OPTICAL_POLL_SKIPPED"))
        }
        return withDuration
    }

    private fun SignalPollResult.toDto(): SignalPollResultDto {
        return SignalPollResultDto(
            slotsPolled = slotsPolled,
            portsPolled = portsPolled,
            portsFailed = portsFailed,
            onusUpdated = onusUpdated,
            polledAtRefreshed = polledAtRefreshed,
            incompleteDiscarded = incompleteDiscarded,
            unchangedSkipped = unchangedSkipped,
            unmatchedRows = unmatchedRows,
            rowsMatched = rowsMatched,
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
        val portsFailed: Int = 0,
        val rows: List<OpticalRow>
    )
}
