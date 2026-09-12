package com.dscorp.wispadmin.oltgateway.snmp

import com.dscorp.wispadmin.oltgateway.config.OltGatewayProperties
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

/**
 * Unified live cycle with multi-varbind GETBULK (~5 min). Requires:
 *   OLT_SNMP_LIVE=true OLT_SNMP_OPTICAL_LIVE=true OLT_GATEWAY_SNMP_RO_COMMUNITY=...
 */
@Tag("live")
class Snmp4jOltSnmpClientMultiVarbindLiveSmokeTest {

    @Test
    fun `ciclo inventario multi-vb + optica per-port multi-vb`() {
        assumeTrue(System.getenv("OLT_SNMP_LIVE") == "true") { "set OLT_SNMP_LIVE=true" }
        assumeTrue(System.getenv("OLT_SNMP_OPTICAL_LIVE") == "true") { "set OLT_SNMP_OPTICAL_LIVE=true" }
        val community = System.getenv("OLT_GATEWAY_SNMP_RO_COMMUNITY").orEmpty()
        assumeTrue(community.isNotBlank()) { "set OLT_GATEWAY_SNMP_RO_COMMUNITY" }

        val props = OltGatewayProperties().apply {
            host = System.getenv("OLT_GATEWAY_HOST") ?: "10.11.104.2"
            snmp.enabled = true
            snmp.port = (System.getenv("OLT_GATEWAY_SNMP_PORT") ?: "161").toInt()
            snmp.roCommunity = community
        }
        assertEquals(20_000L, props.snmp.timeoutMs)
        assertEquals(2, props.snmp.retries)
        assertEquals(25, props.snmp.maxRepetitions)
        assertEquals(1, props.snmp.opticalParallelPorts)
        assertTrue(props.snmp.opticalPerPortWalks)

        val client = Snmp4jOltSnmpClient(props)

        val inventoryStarted = System.currentTimeMillis()
        val onus = client.listConfiguredOnus()
        val inventoryMs = System.currentTimeMillis() - inventoryStarted

        assertTrue(onus.size >= 700, "inventory rows=${onus.size}")
        assertTrue(onus.count { it.runState != null } >= onus.size / 2, "run states missing")
        assertTrue(onus.count { it.sn.length >= 12 } == onus.size, "malformed SNs")

        val ports = onus.map { GponFsp(frame = 0, slot = it.slot, port = it.port) }.distinct()
        assertTrue(ports.size >= 20, "ports=${ports.size}")

        val opticalStarted = System.currentTimeMillis()
        val optical = client.listOptical(ports)
        val opticalMs = System.currentTimeMillis() - opticalStarted

        assertEquals(0, client.lastOpticalWalkPortsFailed(), "ports failed")
        assertEquals(ports.size, client.lastOpticalWalkPortsAttempted())
        assertTrue(optical.size >= onus.size * 8 / 10, "optical rows=${optical.size} onus=${onus.size}")
        val withRx = optical.count { it.onuRxDbm != null }
        assertTrue(withRx >= optical.size / 2, "rows with rx=$withRx of ${optical.size}")

        val totalMs = inventoryMs + opticalMs
        assertTrue(totalMs < 600_000, "unified cycle should stay well under 10 min, was ${totalMs}ms")

        println(
            "LIVE SNMP MULTIVB RESULT host=${props.host} " +
                "inventoryRows=${onus.size} inventoryMs=$inventoryMs " +
                "ports=${ports.size} opticalRows=${optical.size} withRx=$withRx " +
                "opticalMs=$opticalMs totalMs=$totalMs portsFailed=${client.lastOpticalWalkPortsFailed()}"
        )
    }
}
