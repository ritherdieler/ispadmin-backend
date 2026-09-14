package com.dscorp.wispadmin.oltgateway.snmp

import com.dscorp.wispadmin.oltgateway.config.OltGatewayProperties
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

/**
 * Live optical GETBULK (~2–5 min). Requires:
 *   OLT_SNMP_LIVE=true OLT_SNMP_OPTICAL_LIVE=true OLT_GATEWAY_SNMP_RO_COMMUNITY=...
 */
@Tag("live")
class Snmp4jOltSnmpClientOpticalLiveSmokeTest {

    @Test
    fun `cliente SNMP lista optica DDM`() {
        assumeTrue(System.getenv("OLT_SNMP_LIVE") == "true")
        assumeTrue(System.getenv("OLT_SNMP_OPTICAL_LIVE") == "true") { "set OLT_SNMP_OPTICAL_LIVE=true" }
        val community = System.getenv("OLT_GATEWAY_SNMP_RO_COMMUNITY").orEmpty()
        assumeTrue(community.isNotBlank())

        val props = OltGatewayProperties().apply {
            host = System.getenv("OLT_GATEWAY_HOST") ?: "10.11.104.2"
            snmp.enabled = true
            snmp.port = (System.getenv("OLT_GATEWAY_SNMP_PORT") ?: "161").toInt()
            snmp.roCommunity = community
            snmp.timeoutMs = 15000
            snmp.retries = 1
            snmp.maxRepetitions = 25
        }
        val client = Snmp4jOltSnmpClient(props)
        val started = System.currentTimeMillis()
        val optical = client.listOptical()
        val elapsedMs = System.currentTimeMillis() - started

        assertTrue(optical.size >= 100, "optical rows=${optical.size}")
        val withRx = optical.count { it.onuRxDbm != null }
        val sample = optical.first { it.onuRxDbm != null }
        assertTrue(sample.onuRxDbm!! > -40 && sample.onuRxDbm!! < 5, "rx=${sample.onuRxDbm}")

        assertTrue(elapsedMs < 360_000, "full-table optical should finish within 6 min, was ${elapsedMs}ms")

        println(
            "LIVE SNMP OPTICAL OK host=${props.host} rows=${optical.size} " +
                "withRx=$withRx elapsedMs=$elapsedMs " +
                "sample ifIndex=${sample.key.ifIndex} ont=${sample.key.ontId} " +
                "rx=${sample.onuRxDbm} tx=${sample.onuTxDbm} oltRx=${sample.oltRxDbm}"
        )
    }

    @Test
    fun `optica por puerto paralela es mas rapida que full-table`() {
        assumeTrue(System.getenv("OLT_SNMP_LIVE") == "true")
        assumeTrue(System.getenv("OLT_SNMP_OPTICAL_LIVE") == "true")
        val community = System.getenv("OLT_GATEWAY_SNMP_RO_COMMUNITY").orEmpty()
        assumeTrue(community.isNotBlank())

        val props = OltGatewayProperties().apply {
            host = System.getenv("OLT_GATEWAY_HOST") ?: "10.11.104.2"
            snmp.enabled = true
            snmp.port = (System.getenv("OLT_GATEWAY_SNMP_PORT") ?: "161").toInt()
            snmp.roCommunity = community
            snmp.timeoutMs = 20000
            snmp.retries = 1
            snmp.maxRepetitions = 25
            snmp.opticalParallelColumns = true
            snmp.opticalParallelPorts = 6
        }
        val client = Snmp4jOltSnmpClient(props)
        // Sample: 2 ports from live topology (slot 0 port 0 + slot 1 port 0)
        val ports = listOf(GponFsp(0, 0, 0), GponFsp(0, 1, 0))
        val started = System.currentTimeMillis()
        val optical = client.listOptical(ports)
        val elapsedMs = System.currentTimeMillis() - started

        assertTrue(optical.isNotEmpty(), "scoped optical empty")
        assertTrue(elapsedMs < 120_000, "2-port scoped walk should finish <2min, was ${elapsedMs}ms")
        println(
            "LIVE SNMP OPTICAL SCOPED OK ports=${ports.size} rows=${optical.size} elapsedMs=$elapsedMs"
        )
    }
}
