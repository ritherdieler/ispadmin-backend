package com.dscorp.wispadmin.oltgateway.service.inventory

import com.dscorp.wispadmin.oltgateway.exception.OltUnreachableException
import com.dscorp.wispadmin.oltgateway.parser.BoardParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class OltGponTopologyDiscoveryUnreachableTest {

    private val parser = BoardParser()

    @Test
    fun `lanza OltUnreachableException cuando todos los probes fallan por conectividad`() {
        val discovery = OltGponTopologyDiscovery(parser) {
            throw OltUnreachableException("Unable to reach OLT at 10.11.104.2:22")
        }

        val ex = assertThrows(OltUnreachableException::class.java) {
            discovery.discover(maxSlotProbe = 2, defaultPortsPerGponBoard = 16)
        }
        assertEquals("Unable to reach OLT at 10.11.104.2:22", ex.message)
    }

    @Test
    fun `no lanza cuando el OLT responde aunque no haya placas GPON`() {
        val discovery = OltGponTopologyDiscovery(parser) {
            "% Parameter error, the error locates at '^'\n"
        }

        val slots = discovery.discover(maxSlotProbe = 2, defaultPortsPerGponBoard = 16)

        assertEquals(0, slots.size)
    }
}
