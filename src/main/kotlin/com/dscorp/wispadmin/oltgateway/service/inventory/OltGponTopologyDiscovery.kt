package com.dscorp.wispadmin.oltgateway.service.inventory

import com.dscorp.wispadmin.oltgateway.parser.BoardParser
import com.dscorp.wispadmin.oltgateway.parser.ParsedBoard
import org.slf4j.LoggerFactory

class OltGponTopologyDiscovery(
    private val boardParser: BoardParser,
    private val runCommand: (String) -> String
) {

    companion object {
        private val logger = LoggerFactory.getLogger(OltGponTopologyDiscovery::class.java)
    }

    fun discover(maxSlotProbe: Int, defaultPortsPerGponBoard: Int): List<GponSlotInfo> {
        val bySlot = linkedMapOf<Int, ParsedBoard>()
        for (probe in 0..maxSlotProbe.coerceAtLeast(0)) {
            try {
                val output = runCommand("display board $probe")
                for (parsed in boardParser.parseAll(output)) {
                    bySlot.putIfAbsent(parsed.slot, parsed)
                }
            } catch (ex: Exception) {
                logger.warn("Board probe failed for frame/slot {}: {}", probe, ex.message)
            }
        }
        val slots = bySlot.values.mapNotNull { parsed ->
            if (!GponBoardClassifier.isGponBoard(parsed.boardName, parsed.status)) {
                return@mapNotNull null
            }
            val ports = GponBoardClassifier.defaultPortCount(parsed.boardName, defaultPortsPerGponBoard)
            GponSlotInfo(slot = parsed.slot, boardName = parsed.boardName, portCount = ports)
        }
        logger.info("Discovered {} GPON slots: {}", slots.size, slots.map { it.slot })
        return slots
    }
}
