package com.dscorp.wispadmin.oltgateway.service.inventory

import com.dscorp.wispadmin.oltgateway.parser.BoardParser
import com.dscorp.wispadmin.oltgateway.parser.FixtureLoader
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OltGponTopologyDiscoveryTest {

    private val parser = BoardParser()

    @Test
    fun `descubre ambos slots GPON desde display board frame 0 chassis`() {
        val outputByCommand = mapOf(
            "display board 0" to FixtureLoader.load("display-board-frame-0-chassis.txt"),
            "display board 1" to "                      ^\n  % Parameter error, the error locates at '^'\n",
            "display board 2" to "                      ^\n  % Parameter error, the error locates at '^'\n"
        )
        val discovery = OltGponTopologyDiscovery(parser) { cmd ->
            outputByCommand[cmd] ?: ""
        }

        val slots = discovery.discover(maxSlotProbe = 2, defaultPortsPerGponBoard = 16)

        assertEquals(listOf(0, 1), slots.map { it.slot })
        assertEquals("H805GPFD", slots[0].boardName)
        assertEquals("H806GPFD", slots[1].boardName)
        assertEquals(16, slots[0].portCount)
        assertEquals(16, slots[1].portCount)
    }

    @Test
    fun `sigue soportando detalle por slot cuando el OLT responde Board Name`() {
        val discovery = OltGponTopologyDiscovery(parser) { cmd ->
            when (cmd) {
                "display board 0" -> FixtureLoader.load("display-board-0.txt")
                "display board 1" -> FixtureLoader.load("display-board-1.txt")
                else -> "Board Name  : H801MCUD1\nBoard Status: Normal\n"
            }
        }

        val slots = discovery.discover(maxSlotProbe = 1, defaultPortsPerGponBoard = 16)

        assertEquals(2, slots.size)
        assertTrue(slots.any { it.slot == 0 && it.boardName == "H805GPFD" })
        assertTrue(slots.any { it.slot == 1 && it.boardName == "H806GPFD" })
    }
}
