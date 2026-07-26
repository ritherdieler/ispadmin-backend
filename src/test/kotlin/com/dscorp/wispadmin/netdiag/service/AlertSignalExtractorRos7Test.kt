package com.dscorp.wispadmin.netdiag.service

import com.dscorp.wispadmin.netdiag.config.NetDiagProperties
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AlertSignalExtractorRos7Test {

    private val properties = NetDiagProperties().apply {
        optical.rxLowDbm = -14.0
        optical.txFaultDbm = -40.0
    }
    private val extractor = AlertSignalExtractor(properties)

    @Test
    fun `detecta UPSTREAM_PROBE_FAIL cuando netwatch esta down`() {
        val snapshot = baseSnapshot().copy(
            netwatch = listOf(
                NetwatchSnapshot(
                    name = "upstream-http",
                    host = "1.1.1.1",
                    status = "down",
                    type = "http-get",
                    since = null,
                    comment = "upstream"
                )
            )
        )

        val signals = extractor.fromSnapshot(1L, snapshot)

        assertEquals("UPSTREAM_PROBE_FAIL", signals.single().reasonCode)
        assertEquals("P0", signals.single().severity)
        assertTrue(signals.single().dedupKey.contains("upstream-http"))
    }

    @Test
    fun `detecta OPTICAL_RX_LOW y OPTICAL_TX_FAULT`() {
        val snapshot = baseSnapshot().copy(
            optical = listOf(
                OpticalSnapshot(
                    interfaceName = "sfp-sfpplus1",
                    rxPowerDbm = -18.0,
                    txPowerDbm = -45.0,
                    temperatureC = 40.0,
                    sfpPresent = true
                )
            )
        )

        val codes = extractor.fromSnapshot(1L, snapshot).map { it.reasonCode }.toSet()

        assertTrue(codes.containsAll(setOf("OPTICAL_RX_LOW", "OPTICAL_TX_FAULT")))
    }

    @Test
    fun `no alerta optica si potencia en rango`() {
        val snapshot = baseSnapshot().copy(
            optical = listOf(
                OpticalSnapshot(
                    interfaceName = "sfp-sfpplus1",
                    rxPowerDbm = -10.0,
                    txPowerDbm = 1.0,
                    temperatureC = 35.0,
                    sfpPresent = true
                )
            )
        )

        assertTrue(extractor.fromSnapshot(1L, snapshot).none {
            it.reasonCode.startsWith("OPTICAL_")
        })
    }

    private fun baseSnapshot(): PollSnapshot {
        return PollSnapshot(
            interfaces = emptyList(),
            health = emptyList(),
            routerboard = null,
            resource = null,
            criticalInterfaces = emptyList(),
            expectedFirmware = null,
            previousUptimeSeconds = null
        )
    }
}
