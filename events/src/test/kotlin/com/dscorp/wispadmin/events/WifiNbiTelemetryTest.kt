package com.dscorp.wispadmin.events

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class WifiNbiTelemetryTest {
    private val now = Instant.parse("2026-09-08T18:00:00Z")
    private fun device() = ObjectMapper().createObjectNode().put("_id", "B46415-V2804AX15T-12345B4641531C0B6").put("_lastInform", now.toString())
    private fun put(root: ObjectNode, path: String, value: Any, at: Instant = now) {
        var node = root
        path.split('.').forEach { part -> node = (node.get(part) as? ObjectNode) ?: node.putObject(part) }
        node.put("_value", value.toString())
        node.put("_timestamp", at.toString())
    }

    @Test
    fun `V2804AX15T empty associations still yields complete payload`() {
        val root = device()
        put(root, "${WifiNbiTelemetry.ROOT}.WLANConfiguration.1.TotalAssociations", 0)
        put(root, "${WifiNbiTelemetry.ROOT}.WLANConfiguration.5.TotalAssociations", 0)
        val payload = WifiNbiTelemetry.parsePayload(root, "V2804AX15T", "12345B4641531C0B6", now)!!
        assertTrue(payload.complete)
        assertEquals(0, payload.associatedDeviceCount)
        assertTrue(payload.stations.isEmpty())
        assertEquals("FRESH", payload.qualityStatus)
    }

    @Test
    fun `V2804AX15T stations keep normalized mac and band mapping`() {
        val root = device()
        put(root, "${WifiNbiTelemetry.ROOT}.WLANConfiguration.1.TotalAssociations", 1)
        put(root, "${WifiNbiTelemetry.ROOT}.WLANConfiguration.5.TotalAssociations", 0)
        val base = "${WifiNbiTelemetry.ROOT}.WLANConfiguration.1.AssociatedDevice.1"
        put(root, "$base.AssociatedDeviceMACAddress", "aa:bb:cc:dd:ee:ff")
        put(root, "$base.X_HW_RSSI", -42)
        val payload = WifiNbiTelemetry.parsePayload(root, "V2804AX15T", "12345B4641531C0B6", now)!!
        assertTrue(payload.complete)
        assertEquals(1, payload.associated5g)
        assertEquals(0, payload.associated2g)
        assertEquals("AABBCCDDEEFF", payload.stations.single().macNormalized)
        assertEquals("5", payload.stations.single().band)
        assertEquals(-42.0, payload.stations.single().rssi)
    }

    @Test
    fun `stale totals are incomplete and keep empty station list for notify`() {
        val root = device()
        put(root, "${WifiNbiTelemetry.ROOT}.WLANConfiguration.1.TotalAssociations", 1, now.minusSeconds(3600))
        put(root, "${WifiNbiTelemetry.ROOT}.WLANConfiguration.5.TotalAssociations", 0, now.minusSeconds(3600))
        val payload = WifiNbiTelemetry.parsePayload(root, "V2804AX15T", "12345B4641531C0B6", now)!!
        assertFalse(payload.complete)
        assertNull(payload.associatedDeviceCount)
        assertTrue(payload.stations.isEmpty())
        assertEquals("PARAMETERS_NOT_REFRESHED_FOR_INFORM", payload.errorReason)
    }

    @Test
    fun `snFromDeviceId extracts serial suffix`() {
        assertEquals("12345B4641531C0B6", WifiNbiTelemetry.snFromDeviceId("B46415-V2804AX15T-12345B4641531C0B6"))
        assertNull(WifiNbiTelemetry.snFromDeviceId("bad"))
    }
}
