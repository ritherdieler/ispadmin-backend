package com.dscorp.wispadmin.oltgateway.snmp

import com.dscorp.wispadmin.oltgateway.config.OltGatewayProperties
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

/**
 * Live fused pass (inventory + optical in one per-port multi-varbind walk).
 * Requires: OLT_SNMP_LIVE=true OLT_SNMP_OPTICAL_LIVE=true OLT_GATEWAY_SNMP_RO_COMMUNITY=...
 */
@Tag("live")
class Snmp4jOltSnmpClientFusedLiveSmokeTest {

    @Test
    fun `una pasada fusionada cubre los 32 puertos con inventario y optica`() {
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
        assertTrue(props.snmp.fusedInventoryOptical)
        assertTrue(props.snmp.opticalPerPortWalks)
        assertEquals(1, props.snmp.opticalParallelPorts)

        val boards = (System.getenv("OLT_SNMP_FUSED_BOARDS") ?: "0,1").split(",").map { it.trim().toInt() }
        val portsPerBoard = (System.getenv("OLT_SNMP_FUSED_PORTS_PER_BOARD") ?: "16").toInt()
        val ports = boards.flatMap { slot -> (0 until portsPerBoard).map { GponFsp(frame = 0, slot = slot, port = it) } }

        val client = Snmp4jOltSnmpClient(props)

        val started = System.currentTimeMillis()
        val snapshot = client.listInventoryAndOptical(ports)
        val fusedMs = System.currentTimeMillis() - started

        assertEquals(ports.size, snapshot.portsAttempted)
        assertEquals(0, snapshot.portsFailed, "ports failed")
        assertTrue(snapshot.onus.size >= 700, "inventory rows=${snapshot.onus.size}")
        assertTrue(snapshot.onus.all { it.sn.length >= 12 }, "malformed SNs")
        assertTrue(
            snapshot.onus.count { it.runState != null } >= snapshot.onus.size / 2,
            "run states missing"
        )
        val withRx = snapshot.optical.count { it.onuRxDbm != null }
        assertTrue(
            snapshot.optical.size >= snapshot.onus.size * 8 / 10,
            "optical rows=${snapshot.optical.size} onus=${snapshot.onus.size}"
        )
        assertTrue(withRx >= snapshot.optical.size / 2, "rows with rx=$withRx of ${snapshot.optical.size}")
        assertTrue(fusedMs < 407_900, "fused pass must beat the split cycle, was ${fusedMs}ms")

        val occupiedPorts = snapshot.onus.map { it.slot to it.port }.distinct().size
        println(
            "LIVE SNMP FUSED RESULT host=${props.host} portsScanned=${ports.size} " +
                "occupiedPorts=$occupiedPorts inventoryRows=${snapshot.onus.size} " +
                "opticalRows=${snapshot.optical.size} withRx=$withRx " +
                "fusedMs=$fusedMs portsFailed=${snapshot.portsFailed}"
        )
    }
}
