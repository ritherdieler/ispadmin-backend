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

    private fun informPayload(leaves: Map<String, Any?>, at: Long = now.toEpochMilli()): ObjectNode {
        val mapper = ObjectMapper()
        val node = mapper.createObjectNode()
        node.put("v", 1)
        node.put("serial", "12345B4641531C0B6")
        node.put("deviceId", "B46415-V2804AX15T-12345B4641531C0B6")
        node.put("model", "V2804AX15T")
        node.put("at", at)
        node.put("root", WifiNbiTelemetry.ROOT)
        val target = node.putObject("leaves")
        leaves.forEach { (key, value) -> target.put(key, value?.toString()) }
        return node
    }

    @Test
    fun `provision payload parses without touching the NBI`() {
        val tree = WifiNbiTelemetry.expandInformLeaves(
            informPayload(
                mapOf(
                    "Hosts.HostNumberOfEntries" to 2,
                    "Hosts.Host.1.MACAddress" to "aa:bb:cc:dd:ee:ff",
                    "Hosts.Host.1.HostName" to "laptop",
                    "WLANConfiguration.1.TotalAssociations" to 1,
                    "WLANConfiguration.5.TotalAssociations" to 0,
                    "WLANConfiguration.1.AssociatedDevice.1.AssociatedDeviceMACAddress" to "aa:bb:cc:dd:ee:ff",
                    "WLANConfiguration.1.AssociatedDevice.1.X_HW_RSSI" to -42,
                    "WLANConfiguration.1.AssociatedDevice.1.X_HW_SNR" to 31,
                ),
            ),
        )!!
        val payload = WifiNbiTelemetry.parsePayload(tree, "V2804AX15T", "12345B4641531C0B6", now)!!
        assertTrue(payload.complete)
        assertEquals("B46415-V2804AX15T-12345B4641531C0B6", payload.deviceId)
        assertEquals(now, payload.informAt)
        assertEquals(1, payload.associated5g)
        assertEquals(0, payload.associated2g)
        assertEquals(2, payload.lanDeviceCount)
        val station = payload.stations.single()
        assertEquals("AABBCCDDEEFF", station.macNormalized)
        assertEquals(-42.0, station.rssi)
        assertEquals(31.0, station.snr)
        assertEquals("laptop", station.displayName)
    }

    @Test
    fun `payload leaves are stamped with the session clock so they are never stale`() {
        val tree = WifiNbiTelemetry.expandInformLeaves(
            informPayload(mapOf("WLANConfiguration.1.TotalAssociations" to 0, "WLANConfiguration.5.TotalAssociations" to 0)),
        )!!
        val payload = WifiNbiTelemetry.parsePayload(tree, "V2804AX15T", "12345B4641531C0B6", now)!!
        assertTrue(payload.complete)
        assertNull(payload.errorReason)
        assertEquals(now, payload.observedAt)
    }

    @Test
    fun `unusable payloads fall back to the NBI instead of persisting garbage`() {
        val mapper = ObjectMapper()
        assertNull(WifiNbiTelemetry.expandInformLeaves(informPayload(emptyMap()).put("v", 2)))
        assertNull(WifiNbiTelemetry.expandInformLeaves(informPayload(emptyMap()).put("root", "Device.WiFi")))
        assertNull(WifiNbiTelemetry.expandInformLeaves(informPayload(emptyMap(), at = 0)))
        assertNull(WifiNbiTelemetry.expandInformLeaves(mapper.createObjectNode()))
    }

    @Test
    fun `payload cannot smuggle secrets or escape the root`() {
        val tree = WifiNbiTelemetry.expandInformLeaves(
            informPayload(
                mapOf(
                    "WLANConfiguration.1.TotalAssociations" to 0,
                    "WLANConfiguration.5.TotalAssociations" to 0,
                    "WLANConfiguration.1.KeyPassphrase" to "11111111",
                    "WLANConfiguration.1.SSID" to "puppybb-5",
                    "..ManagementServer.Password" to "leak",
                ),
            ),
        )!!
        val json = tree.toString()
        assertFalse(json.contains("11111111"))
        assertFalse(json.contains("puppybb-5"))
        assertFalse(json.contains("leak"))
    }

    @Test
    fun `fallback projection is a single subtree read and never exposes the passphrase`() {
        val fields = WifiNbiTelemetry.projection().split(',')
        assertTrue(fields.size <= 24, "must fit one NBI chunk, got ${fields.size}")
        assertTrue(fields.contains("${WifiNbiTelemetry.ROOT}.WLANConfiguration.1.AssociatedDevice"))
        assertTrue(fields.contains("${WifiNbiTelemetry.ROOT}.Hosts.Host"))
        assertFalse(fields.contains("${WifiNbiTelemetry.ROOT}.WLANConfiguration.1"))
        assertFalse(fields.contains("${WifiNbiTelemetry.ROOT}.WLANConfiguration.5"))
    }
}
