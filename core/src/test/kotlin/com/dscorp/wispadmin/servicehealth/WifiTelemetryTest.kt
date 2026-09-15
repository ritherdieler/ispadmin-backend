package com.dscorp.wispadmin.servicehealth

import com.dscorp.wispadmin.servicehealth.service.WifiTelemetry
import com.dscorp.wispadmin.servicehealth.domain.Quality
import com.dscorp.wispadmin.servicehealth.domain.WifiCurrent
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
        assertFalse(WifiTelemetry.shouldPersist(reading))
        counts(root,2,1)
        val fresh=WifiTelemetry.parse(root,1,"F6600R",now,secret)!!
        assertEquals(3,fresh.count.associatedDeviceCount)
        assertTrue(WifiTelemetry.shouldPersist(fresh))
    }
    @Test fun `incomplete Inform does not persist a sample or poison current quality`() {
        val root=device(); counts(root,2,1,now.minusSeconds(3600))
        val reading=WifiTelemetry.parse(root,1,"F6600R",now,secret)!!
        val previous=now.minusSeconds(1800)
        val current=WifiCurrent(subscriptionId=1,deviceId="test-device",model="F6600R",informAt=previous,
            observedAt=previous,associatedDeviceCount=3,qualityStatus=Quality.FRESH)
        assertFalse(WifiTelemetry.applyCurrent(current,reading,"test-device","F6600R",now))
        assertEquals(Quality.FRESH,current.qualityStatus)
        assertEquals(3,current.associatedDeviceCount)
        assertEquals(previous,current.observedAt)
        assertEquals(previous,current.informAt)
    }
    @Test fun `complete reading is keyed by wifi timestamp not a later Inform`() {
        val wifiAt=now.minusSeconds(30)
        val root=device(); counts(root,3,0,wifiAt)
        val reading=WifiTelemetry.parse(root,1,"F6600R",now,secret)!!
        assertTrue(reading.complete)
        assertEquals(wifiAt,reading.count.observedAt)
        assertEquals(now,reading.count.informAt)
        assertTrue(WifiTelemetry.shouldPersist(reading))
        val current=WifiCurrent(subscriptionId=1)
        assertTrue(WifiTelemetry.applyCurrent(current,reading,"test-device","F6600R",now))
        assertEquals(wifiAt,current.observedAt)
        assertEquals(now,current.informAt)
        assertEquals(3,current.associatedDeviceCount)
        assertEquals(Quality.FRESH,current.qualityStatus)
    }
    @Test fun `gpv slack allows params a few minutes before the latest inform`() {
        val wifiAt=now.minusSeconds(150)
        val root=device(); counts(root,3,0,wifiAt)
        val reading=WifiTelemetry.parse(root,1,"F6600R",now,secret)!!
        assertTrue(reading.complete)
        assertEquals(3,reading.count.associatedDeviceCount)
        assertTrue(WifiTelemetry.shouldPersist(reading))
    }
    @Test fun `params older than gpv slack still fail inform alignment`() {
        val wifiAt=now.minusSeconds(WifiTelemetry.INFORM_PARAM_MAX_LAG_SECONDS + 60)
        val root=device(); counts(root,2,1,wifiAt)
        val reading=WifiTelemetry.parse(root,1,"F6600R",now,secret)!!
        assertFalse(reading.complete)
        assertFalse(WifiTelemetry.shouldPersist(reading))
    }
    @Test fun `unsupported model is not persisted as a wifi sample`() {
        val reading=WifiTelemetry.parse(device(),1,"HG8145X6",now,secret)!!
        assertEquals(Quality.UNSUPPORTED,reading.count.qualityStatus)
        assertFalse(WifiTelemetry.shouldPersist(reading))
    }
    @Test fun `V2804AX15T maps radio 1 to 5 GHz and radio 5 to 2_4 like TR069 profile`() {
        assertEquals(mapOf(1 to "5", 5 to "2.4"), WifiTelemetry.radios("V2804AX15T"))
    }

    @Test fun `V2804AX15T parse labels WLAN1 stations as 5 GHz and WLAN5 as 2_4`() {
        val root=device()
        put(root,"${WifiTelemetry.ROOT}.WLANConfiguration.1.TotalAssociations",1)
        put(root,"${WifiTelemetry.ROOT}.WLANConfiguration.5.TotalAssociations",1)
        val five="${WifiTelemetry.ROOT}.WLANConfiguration.1.AssociatedDevice.1"
        put(root,"$five.AssociatedDeviceMACAddress","11:22:33:44:55:66")
        put(root,"$five.AssociatedDeviceRssi",-40)
        val twoFour="${WifiTelemetry.ROOT}.WLANConfiguration.5.AssociatedDevice.1"
        put(root,"$twoFour.AssociatedDeviceMACAddress","aa:bb:cc:dd:ee:ff")
        put(root,"$twoFour.AssociatedDeviceRssi",-70)
        val reading=WifiTelemetry.parse(root,1,"V2804AX15T",now,secret)!!
        assertTrue(reading.complete)
        assertEquals(1,reading.count.associated5g)
        assertEquals(1,reading.count.associated2g)
        assertEquals(2,reading.count.associatedDeviceCount)
        assertEquals(setOf("5","2.4"), reading.stations.map { it.band }.toSet())
        assertEquals(-40.0, reading.stations.single { it.band == "5" }.rssi)
        assertEquals(-70.0, reading.stations.single { it.band == "2.4" }.rssi)
    }

    @Test fun `station identity is salted by subscription and mac never leaves parser`() {
        val root=device(); counts(root,1,0)
        val base="${WifiTelemetry.ROOT}.WLANConfiguration.1.AssociatedDevice.1"
        put(root,"$base.AssociatedDeviceMACAddress","aa:bb:cc:dd:ee:ff")
        put(root,"$base.AssociatedDeviceRssi",-80)
        put(root,"${WifiTelemetry.ROOT}.Hosts.Host.1.MACAddress","aa:bb:cc:dd:ee:ff")
        put(root,"${WifiTelemetry.ROOT}.Hosts.Host.1.HostName","iPhone-de-Ana")
        val station=WifiTelemetry.parse(root,1,"F6600R",now,secret)!!.stations.single()
        assertEquals(-80.0,station.rssi)
        assertEquals("iPhone-de-Ana",station.displayName)
        assertEquals(64,station.stationKey.length)
        assertNotEquals(station.stationKey,WifiTelemetry.stationKey(secret,2,"AA-BB-CC-DD-EE-FF"))
        val output=ObjectMapper().findAndRegisterModules().writeValueAsString(station)
        assertTrue(output.contains("iPhone-de-Ana"))
        assertFalse(output.contains("aa:bb"))
        assertFalse(output.contains("AABBCCDDEEFF"))
    }
    @Test fun `AssociatedDeviceName fills display name when Hosts HostName is empty`() {
        val root=device(); counts(root,1,0)
        val base="${WifiTelemetry.ROOT}.WLANConfiguration.1.AssociatedDevice.1"
        put(root,"$base.AssociatedDeviceMACAddress","11:22:33:44:55:66")
        put(root,"$base.AssociatedDeviceRssi",-55)
        put(root,"$base.X_ZTE-COM_AssociatedDeviceName","TV-Sala")
        val station=WifiTelemetry.parse(root,1,"F6600R",now,secret)!!.stations.single()
        assertEquals("TV-Sala",station.displayName)
    }
    @Test fun `unknown model exposes unsupported rather than zero`() {
        val reading=WifiTelemetry.parse(device(),1,"HG8145X6",now,secret)!!
        assertEquals(Quality.UNSUPPORTED,reading.count.qualityStatus); assertNull(reading.count.associatedDeviceCount)
    }
}
