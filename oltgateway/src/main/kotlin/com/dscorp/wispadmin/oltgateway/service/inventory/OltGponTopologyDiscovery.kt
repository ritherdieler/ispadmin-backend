package com.dscorp.wispadmin.oltgateway.service.inventory

import com.dscorp.wispadmin.oltgateway.exception.OltUnreachableException
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
        var unreachableFailures = 0
        var responsiveProbes = 0
        var lastUnreachable: OltUnreachableException? = null
        for (probe in 0..maxSlotProbe.coerceAtLeast(0)) {
            try {
                val output = runCommand("display board $probe")
                responsiveProbes++
                for (parsed in boardParser.parseAll(output)) {
                    bySlot.putIfAbsent(parsed.slot, parsed)
                }
            } catch (ex: Exception) {
                if (isUnreachable(ex)) {
                    unreachableFailures++
                    lastUnreachable = ex as? OltUnreachableException
                        ?: OltUnreachableException(ex.message ?: "Unable to reach OLT", ex)
                } else {
                    responsiveProbes++
                }
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
        if (slots.isEmpty() && unreachableFailures > 0 && responsiveProbes == 0) {
            throw lastUnreachable ?: OltUnreachableException("Unable to reach OLT during topology discovery")
        }
        logger.info("Discovered {} GPON slots: {}", slots.size, slots.map { it.slot })
        return slots
    }

    private fun isUnreachable(ex: Exception): Boolean {
        var current: Throwable? = ex
        while (current != null) {
            if (current is OltUnreachableException) {
                return true
            }
            val message = current.message.orEmpty().lowercase()
            if (message.contains("unable to reach olt")) {
                return true
            }
            current = current.cause
        }
        return false
    }
}
