package com.dscorp.wispadmin.servicehealth

import com.dscorp.wispadmin.servicehealth.service.AcsWifiRefreshPlanner
import com.dscorp.wispadmin.servicehealth.service.WifiTelemetry
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.Instant

class AcsWifiRefreshPlannerTest {
    private val now = Instant.parse("2026-08-31T17:00:00Z")
    private val cooldown = 900L

    @Test
    fun `stale wlan params relative to inform request a gpv after cooldown`() {
        assertTrue(AcsWifiRefreshPlanner.shouldEnqueue("PARAMETERS_NOT_REFRESHED_FOR_INFORM", null, now, cooldown))
        assertTrue(AcsWifiRefreshPlanner.shouldEnqueue("PARAMETERS_NOT_REFRESHED_FOR_INFORM", now.minusSeconds(901), now, cooldown))
        assertFalse(AcsWifiRefreshPlanner.shouldEnqueue("PARAMETERS_NOT_REFRESHED_FOR_INFORM", now.minusSeconds(100), now, cooldown))
        assertFalse(AcsWifiRefreshPlanner.shouldEnqueue(null, null, now, cooldown))
        assertFalse(AcsWifiRefreshPlanner.shouldEnqueue("UNSUPPORTED", null, now, cooldown))
    }

    @Test
    fun `gpv paths cover total associations and known station leaves for F6600R`() {
        val root = ObjectMapper().createObjectNode().put("_id", "dev")
        fun put(path: String) {
            var node: ObjectNode = root
            path.split('.').forEach { part -> node = (node.get(part) as? ObjectNode) ?: node.putObject(part) }
            node.put("_value", "1"); node.put("_timestamp", now.toString())
        }
        put("${WifiTelemetry.ROOT}.WLANConfiguration.1.TotalAssociations")
        put("${WifiTelemetry.ROOT}.WLANConfiguration.5.TotalAssociations")
        put("${WifiTelemetry.ROOT}.WLANConfiguration.1.AssociatedDevice.1.AssociatedDeviceMACAddress")
        put("${WifiTelemetry.ROOT}.WLANConfiguration.1.AssociatedDevice.1.AssociatedDeviceRssi")
        val paths = WifiTelemetry.gpvPaths(root, "F6600R")
        assertTrue(paths.contains("${WifiTelemetry.ROOT}.WLANConfiguration.1.TotalAssociations"))
        assertTrue(paths.contains("${WifiTelemetry.ROOT}.WLANConfiguration.5.TotalAssociations"))
        assertTrue(paths.any { it.endsWith("AssociatedDeviceRssi") })
        assertTrue(paths.size >= 3)
    }

    @Test
    fun `unsupported model yields no gpv paths`() {
        assertTrue(WifiTelemetry.gpvPaths(ObjectMapper().createObjectNode(), "HG8145X6").isEmpty())
    }
}
