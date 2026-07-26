package com.dscorp.wispadmin.netdiag.service

import com.dscorp.wispadmin.netdiag.config.NetDiagProperties
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AlertSignalExtractorTest {

    private val properties = NetDiagProperties().apply {
        alert.cpuThreshold = 85
        alert.lowVoltage = 20.0
    }
    private val extractor = AlertSignalExtractor(properties)

    @Test
    fun `detecta LINK_DOWN en interfaz critica`() {
        val snapshot = PollSnapshot(
            interfaces = listOf(
                InterfaceSnapshot("ether1", "ether", running = false, disabled = false),
                InterfaceSnapshot("ether2", "ether", running = false, disabled = false)
            ),
            health = emptyList(),
            routerboard = null,
            resource = null,
            criticalInterfaces = listOf("ether1"),
            expectedFirmware = null,
            previousUptimeSeconds = null
        )

        val signals = extractor.fromSnapshot(targetId = 1L, snapshot = snapshot)

        assertEquals(1, signals.size)
        assertEquals("LINK_DOWN", signals[0].reasonCode)
        assertTrue(signals[0].dedupKey.contains("ether1"))
    }

    @Test
    fun `detecta GRE_TUNNEL_DOWN`() {
        val snapshot = PollSnapshot(
            interfaces = listOf(
                InterfaceSnapshot("gre-tunnel1", "gre-tunnel", running = false, disabled = false)
            ),
            health = emptyList(),
            routerboard = null,
            resource = null,
            criticalInterfaces = emptyList(),
            expectedFirmware = null,
            previousUptimeSeconds = null
        )

        val signals = extractor.fromSnapshot(1L, snapshot)

        assertEquals("GRE_TUNNEL_DOWN", signals.single().reasonCode)
    }

    @Test
    fun `detecta PSU FAN LOW_VOLTAGE FIRMWARE_DRIFT CPU_HIGH UNEXPECTED_REBOOT`() {
        val snapshot = PollSnapshot(
            interfaces = emptyList(),
            health = listOf(
                HealthSnapshot("psu1-state", "fail"),
                HealthSnapshot("fan1-state", "fail"),
                HealthSnapshot("voltage", "18.5")
            ),
            routerboard = RouterboardSnapshot(currentFirmware = "7.12", upgradeFirmware = "7.23.2"),
            resource = ResourceSnapshot(
                uptimeRaw = "5m",
                uptimeSeconds = 300,
                cpuLoad = 95,
                version = "7.12"
            ),
            criticalInterfaces = emptyList(),
            expectedFirmware = "7.23.2",
            previousUptimeSeconds = 90000
        )

        val codes = extractor.fromSnapshot(1L, snapshot).map { it.reasonCode }.toSet()

        assertTrue(codes.containsAll(setOf(
            "PSU_FAIL",
            "FAN_FAIL",
            "LOW_VOLTAGE",
            "FIRMWARE_DRIFT",
            "CPU_HIGH",
            "UNEXPECTED_REBOOT"
        )))
    }

    @Test
    fun `mapea fallo de poll a DEVICE_UNREACHABLE`() {
        val signals = extractor.fromPollFailure(1L, "DEVICE_UNREACHABLE", "timeout")
        assertEquals("DEVICE_UNREACHABLE", signals.single().reasonCode)
        assertEquals("P0", signals.single().severity)
    }
}
