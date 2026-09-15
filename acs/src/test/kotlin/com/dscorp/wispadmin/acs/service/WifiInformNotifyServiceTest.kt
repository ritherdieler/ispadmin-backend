package com.dscorp.wispadmin.acs.service

import com.dscorp.wispadmin.acs.entity.CpeRecord
import com.dscorp.wispadmin.acs.genieacs.GenieAcsClient
import com.dscorp.wispadmin.acs.repository.CpeRecordRepository
import com.dscorp.wispadmin.events.CpeInformPayload
import com.dscorp.wispadmin.events.WifiNbiTelemetry
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.Optional

class WifiInformNotifyServiceTest {
    private val records = mockk<CpeRecordRepository>()
    private val client = mockk<GenieAcsClient>()
    private val gateway = mockk<AcsToGatewayInformClient>(relaxed = true)
    private val json = ObjectMapper().registerModule(JavaTimeModule())
    private val now = Instant.parse("2026-09-08T18:00:00Z")
    private val service = WifiInformNotifyService(records, client, gateway, json).also { it.nowProvider = { now } }

    private fun deviceTree(): ObjectNode {
        val root = json.createObjectNode().put("_id", "B46415-V2804AX15T-12345B4641531C0B6").put("_lastInform", now.toString())
        fun put(path: String, value: Any) {
            var node: ObjectNode = root
            path.split('.').forEach { part -> node = (node.get(part) as? ObjectNode) ?: node.putObject(part) }
            node.put("_value", value.toString())
            node.put("_timestamp", now.toString())
        }
        put("${WifiNbiTelemetry.ROOT}.WLANConfiguration.1.TotalAssociations", 0)
        put("${WifiNbiTelemetry.ROOT}.WLANConfiguration.5.TotalAssociations", 0)
        return root
    }

    @Test
    fun `parses nbi last state and posts gateway even with empty stations`() {
        val record = CpeRecord(sn = "12345B4641531C0B6", deviceId = "B46415-V2804AX15T-12345B4641531C0B6", productClass = "V2804AX15T")
        every { records.findById("12345B4641531C0B6") } returns Optional.of(record)
        every { records.save(any()) } answers { firstArg() }
        every { client.readDeviceCache(listOf(record.deviceId!!), any()) } returns listOf(deviceTree())
        val result = service.notify(deviceId = record.deviceId, serial = record.sn)
        assertTrue(result.accepted)
        verify {
            gateway.postInform(
                match<CpeInformPayload> {
                    it.sn == "12345B4641531C0B6" &&
                        it.complete &&
                        it.associatedDeviceCount == 0 &&
                        it.stations.isEmpty()
                },
            )
        }
        assertEquals(now, record.lastInformAt)
        assertTrue(!record.wifiSnapshotJson.isNullOrBlank())
    }

    private fun informPayload(): String = json.writeValueAsString(
        mapOf(
            "v" to 1,
            "serial" to "12345B4641531C0B6",
            "deviceId" to "B46415-V2804AX15T-12345B4641531C0B6",
            "model" to "V2804AX15T",
            "at" to now.toEpochMilli(),
            "root" to WifiNbiTelemetry.ROOT,
            "leaves" to mapOf(
                "WLANConfiguration.1.TotalAssociations" to "1",
                "WLANConfiguration.5.TotalAssociations" to "0",
                "WLANConfiguration.1.AssociatedDevice.1.AssociatedDeviceMACAddress" to "aa:bb:cc:dd:ee:ff",
                "WLANConfiguration.1.AssociatedDevice.1.X_HW_RSSI" to "-42",
            ),
        ),
    )

    @Test
    fun `inform payload is used without reading the nbi`() {
        val record = CpeRecord(sn = "12345B4641531C0B6", deviceId = "B46415-V2804AX15T-12345B4641531C0B6", productClass = "V2804AX15T")
        every { records.findById("12345B4641531C0B6") } returns Optional.of(record)
        every { records.save(any()) } answers { firstArg() }
        val result = service.notify(deviceId = record.deviceId, serial = record.sn, payload = informPayload())
        assertTrue(result.accepted)
        verify(exactly = 0) { client.readDeviceCache(any(), any()) }
        verify {
            gateway.postInform(
                match<CpeInformPayload> {
                    it.complete && it.associated5g == 1 && it.stations.single().macNormalized == "AABBCCDDEEFF"
                },
            )
        }
    }

    @Test
    fun `unusable payload falls back to the nbi read`() {
        val record = CpeRecord(sn = "12345B4641531C0B6", deviceId = "B46415-V2804AX15T-12345B4641531C0B6", productClass = "V2804AX15T")
        every { records.findById("12345B4641531C0B6") } returns Optional.of(record)
        every { records.save(any()) } answers { firstArg() }
        every { client.readDeviceCache(listOf(record.deviceId!!), any()) } returns listOf(deviceTree())
        val result = service.notify(deviceId = record.deviceId, serial = record.sn, payload = "{\"v\":99}")
        assertTrue(result.accepted)
        verify(exactly = 1) { client.readDeviceCache(listOf(record.deviceId!!), any()) }
    }
}
