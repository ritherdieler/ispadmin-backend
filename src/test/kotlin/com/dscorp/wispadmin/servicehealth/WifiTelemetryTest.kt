package com.dscorp.wispadmin.servicehealth

import com.dscorp.wispadmin.servicehealth.service.WifiTelemetry
import com.dscorp.wispadmin.servicehealth.domain.Quality
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.Instant

class WifiTelemetryTest {
    private val now=Instant.parse("2026-08-30T15:00:00Z")
    private val secret="test-only-key-of-at-least-32-bytes-long"
    private fun device()=ObjectMapper().createObjectNode().put("_id","test-device").put("_lastInform",now.toString())
    private fun put(root: ObjectNode,path: String,value: Any,at: Instant=now) {
        var node=root
        path.split('.').forEach { part -> node=(node.get(part) as? ObjectNode) ?: node.putObject(part) }
        node.put("_value",value.toString()); node.put("_timestamp",at.toString())
    }
    private fun counts(root: ObjectNode,a: Int=0,b: Int=0,at: Instant=now) {
        put(root,"${WifiTelemetry.ROOT}.WLANConfiguration.1.TotalAssociations",a,at)
        put(root,"${WifiTelemetry.ROOT}.WLANConfiguration.5.TotalAssociations",b,at)
    }
    @Test fun `a real zero is fresh but a missing count is not zero`() {
        val root=device(); counts(root)
        val valid=WifiTelemetry.parse(root,1,"F6600R",now,secret)!!
        assertEquals(0,valid.count.associatedDeviceCount); assertEquals(Quality.FRESH,valid.count.qualityStatus)
        val absent=WifiTelemetry.parse(device(),1,"F6600R",now,secret)!!
        assertNull(absent.count.associatedDeviceCount); assertEquals(Quality.MISSING,absent.count.qualityStatus)
    }
    @Test fun `new Inform cannot refresh old parameter values`() {
        val root=device(); counts(root,2,1,now.minusSeconds(3600))
        val reading=WifiTelemetry.parse(root,1,"F6600R",now,secret)!!
        assertFalse(reading.complete); assertNull(reading.count.associatedDeviceCount)
        counts(root,2,1)
        assertEquals(3,WifiTelemetry.parse(root,1,"F6600R",now,secret)!!.count.associatedDeviceCount)
    }
    @Test fun `station identity is salted by subscription and names never leave parser`() {
        val root=device(); counts(root,1,0)
        val base="${WifiTelemetry.ROOT}.WLANConfiguration.1.AssociatedDevice.1"
        put(root,"$base.AssociatedDeviceMACAddress","aa:bb:cc:dd:ee:ff")
        put(root,"$base.AssociatedDeviceRssi",-80)
        put(root,"$base.X_ZTE-COM_AssociatedDeviceName","private-name")
        val station=WifiTelemetry.parse(root,1,"F6600R",now,secret)!!.stations.single()
        assertEquals(-80.0,station.rssi)
        assertEquals(64,station.stationKey.length)
        assertNotEquals(station.stationKey,WifiTelemetry.stationKey(secret,2,"AA-BB-CC-DD-EE-FF"))
        val output=ObjectMapper().findAndRegisterModules().writeValueAsString(station)
        assertFalse(output.contains("private-name")); assertFalse(output.contains("aa:bb"))
    }
    @Test fun `unknown model exposes unsupported rather than zero`() {
        val reading=WifiTelemetry.parse(device(),1,"HG8145X6",now,secret)!!
        assertEquals(Quality.UNSUPPORTED,reading.count.qualityStatus); assertNull(reading.count.associatedDeviceCount)
    }
    @Test fun `projection excludes trees credentials names and per station byte counters`() {
        val projection=WifiTelemetry.projection()
        assertFalse(projection.contains("Hosts.Host."))
        listOf("HostName","AssociatedDeviceName","SSID","Password","KeyPassphrase","BytesSent","BytesReceived").forEach { assertFalse(projection.contains(it),it) }
        assertTrue(projection.contains("Hosts.HostNumberOfEntries"))
    }
}
