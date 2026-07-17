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
import java.util.concurrent.atomic.AtomicReference

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
        val jobs = InventoryJobPlanner.plan(topology, maxSessions = 1)
        if (jobs.isEmpty()) {
            logger.warn("Inventory produced no CLI jobs (slots={})", topology.size)
            return emptyList()
        }
        val results = mutableListOf<ParsedOnuSummary>()
        for (job in jobs) {
            try {
                val output = session.execute(job.command())
                results += onuSummaryParser.parse(output)
            } catch (ex: Exception) {
                logger.warn("Inventory job failed command={}: {}", job.command(), ex.message)
            }
        }
        val merged = mergeBySn(results)
        logger.info(
            "Inventory complete slots={} jobs={} onus={} workers=1 durationMs={}",
            topology.size,
            jobs.size,
            merged.size,
            System.currentTimeMillis() - started
        )
        return merged
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
}
