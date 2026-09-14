package com.dscorp.wispadmin.oltgateway.service.inventory

import com.dscorp.wispadmin.oltgateway.config.OltGatewayProperties
import com.dscorp.wispadmin.oltgateway.domain.repository.OltMgrOltRepository
import com.dscorp.wispadmin.oltgateway.parser.BoardParser
import com.dscorp.wispadmin.oltgateway.parser.OnuSummaryParser
import com.dscorp.wispadmin.oltgateway.parser.ParsedOnuSummary
import com.dscorp.wispadmin.oltgateway.exception.CliBusBusyException
import com.dscorp.wispadmin.oltgateway.ssh.CliBusResult
import com.dscorp.wispadmin.oltgateway.ssh.CliJobType
import com.dscorp.wispadmin.oltgateway.ssh.HuaweiCliSession
import com.dscorp.wispadmin.oltgateway.ssh.OltCliBus
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

/**
 * SSH inventory reader (`display ont info 0/{slot} all`).
 * Deprecated: inventory and live listOnus use SNMP [OltSnmpClient.listConfiguredOnus] when enabled.
 * Kept only for [OltGatewayProperties.SnmpProperties.allowSshInventoryFallback].
 */
@Deprecated("SSH inventory is deprecated; use SNMP listConfiguredOnus()")
class ParallelOnuInventoryReader(
    private val cliBus: OltCliBus,
    private val boardParser: BoardParser,
    private val onuSummaryParser: OnuSummaryParser,
    private val oltRepository: OltMgrOltRepository,
    private val properties: OltGatewayProperties
) {

    companion object {
        private val logger = LoggerFactory.getLogger(ParallelOnuInventoryReader::class.java)
    }

    private val topologyCache = AtomicReference<CachedTopology?>(null)
    private val slotPortFallbackSlots = ConcurrentHashMap.newKeySet<Int>()

    fun listOnusParsed(): List<ParsedOnuSummary> {
        return when (val result = cliBus.execute(CliJobType.INVENTORY) { session ->
            readInventory(session)
        }) {
            is CliBusResult.Ok -> result.value
            is CliBusResult.Skipped -> throw CliBusBusyException(result.reason)
        }
    }

    private fun readInventory(session: HuaweiCliSession): List<ParsedOnuSummary> {
        val started = System.currentTimeMillis()
        val topology = discoverTopology(session)
        if (topology.isEmpty()) {
            logger.warn("Inventory produced no GPON slots")
            return emptyList()
        }
        val results = mutableListOf<ParsedOnuSummary>()
        val failures = mutableListOf<Exception>()
        var jobsRun = 0
        for (slot in topology) {
            val slotRead = readSlot(session, slot)
            jobsRun += slotRead.jobsRun
            results += slotRead.items
            if (slotRead.items.isEmpty() && slotRead.failures.isNotEmpty()) {
                failures += slotRead.failures
            }
        }
        val merged = mergeBySn(results)
        if (merged.isEmpty() && failures.isNotEmpty()) {
            throw failures.first()
        }
        logger.info(
            "Inventory complete slots={} jobs={} onus={} workers=1 durationMs={}",
            topology.size,
            jobsRun,
            merged.size,
            System.currentTimeMillis() - started
        )
        return merged
    }

    private fun readSlot(session: HuaweiCliSession, slot: GponSlotInfo): SlotReadResult {
        if (slot.slot in slotPortFallbackSlots) {
            return readSlotPorts(session, slot)
        }
        val slotAll = InventoryCliJob.SlotAll(slot.slot)
        try {
            val output = session.execute(slotAll.command(), properties.inventory.slotAllProbeTimeoutMs)
            val parsed = onuSummaryParser.parse(output)
            if (parsed.isNotEmpty()) {
                return SlotReadResult(parsed, jobsRun = 1, failures = emptyList())
            }
            logger.warn("Inventory slot {} slot-all returned no ONUs command={}", slot.slot, slotAll.command())
        } catch (ex: Exception) {
            logger.warn(
                "Inventory slot {} slot-all failed command={}, falling back to port-all: {}",
                slot.slot,
                slotAll.command(),
                ex.message
            )
        }
        slotPortFallbackSlots.add(slot.slot)
        return readSlotPorts(session, slot)
    }

    private fun readSlotPorts(session: HuaweiCliSession, slot: GponSlotInfo): SlotReadResult {
        val results = mutableListOf<ParsedOnuSummary>()
        val failures = mutableListOf<Exception>()
        var jobsRun = 0
        for (port in 0 until slot.portCount) {
            val job = InventoryCliJob.SlotPort(slot.slot, port)
            jobsRun++
            try {
                val output = session.execute(job.command())
                results += onuSummaryParser.parse(output)
            } catch (ex: Exception) {
                logger.warn("Inventory job failed command={}: {}", job.command(), ex.message)
                failures += ex
            }
        }
        return SlotReadResult(results, jobsRun, failures)
    }

    private fun discoverTopology(session: HuaweiCliSession): List<GponSlotInfo> {
        val cached = topologyCache.get()
        val now = System.currentTimeMillis()
        if (cached != null && now - cached.atMs < properties.inventory.topologyCacheTtlMs) {
            return cached.slots
        }
        val model = resolveModelLimits()
        val discovery = OltGponTopologyDiscovery(boardParser) { command -> session.execute(command) }
        val slots = discovery.discover(
            maxSlotProbe = model.maxSlotProbe,
            defaultPortsPerGponBoard = model.defaultPorts
        )
        topologyCache.set(CachedTopology(slots, now))
        return slots
    }

    private fun resolveModelLimits(): ModelLimits {
        val model = oltRepository.findByName(properties.oltId).map { it.model }.orElse(null)
        return ModelLimits(
            maxSlotProbe = model?.maxSlotProbe ?: properties.inventory.maxSlotProbe,
            defaultPorts = model?.defaultPortsPerGponBoard ?: properties.inventory.defaultPortsPerGponBoard
        )
    }

    private fun mergeBySn(items: List<ParsedOnuSummary>): List<ParsedOnuSummary> {
        val bySn = linkedMapOf<String, ParsedOnuSummary>()
        for (item in items) {
            bySn[item.sn.uppercase()] = item
        }
        return bySn.values.toList()
    }

    private data class CachedTopology(val slots: List<GponSlotInfo>, val atMs: Long)

    private data class ModelLimits(val maxSlotProbe: Int, val defaultPorts: Int)

    private data class SlotReadResult(
        val items: List<ParsedOnuSummary>,
        val jobsRun: Int,
        val failures: List<Exception>
    )
}
