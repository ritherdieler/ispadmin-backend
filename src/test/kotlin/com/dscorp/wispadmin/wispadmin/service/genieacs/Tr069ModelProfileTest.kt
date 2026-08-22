package com.dscorp.wispadmin.wispadmin.service.genieacs

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class Tr069ModelProfileTest {

    @Test
    fun `resolves VSOL V2804AX15T from onu type name`() {
        val profile = Tr069ModelProfiles.resolveBuiltin(onuTypeName = "V2804AX15T", productClass = null)
        assertNotNull(profile)
        assertEquals("V2804AX15T", profile!!.productClass)
        assertTrue(profile.wanIpConnectionPath.contains("WANConnectionDevice.1.WANIPConnection.1"))
        assertTrue(profile.wlan24Path.endsWith("WLANConfiguration.5"))
        assertTrue(profile.wlan5Path.endsWith("WLANConfiguration.1"))
    }

    @Test
    fun `resolves VSOL profile from product class`() {
        val profile = Tr069ModelProfiles.resolveBuiltin(onuTypeName = "unknown", productClass = "V2804AX15T")
        assertNotNull(profile)
    }

    @Test
    fun `resolves VSOLVA74 SmartOLT onu type to V2804AX15T profile`() {
        val profile = Tr069ModelProfiles.resolveBuiltin(onuTypeName = "VSOLVA74", productClass = null)
        assertNotNull(profile)
        assertEquals("V2804AX15T", profile!!.productClass)
    }

    @Test
    fun `returns null for unknown model`() {
        Tr069ModelProfiles.registerDynamicResolver { _, _ -> null }
        assertNull(Tr069ModelProfiles.resolve(onuTypeName = "HG8310", productClass = "HG8310"))
        assertNull(Tr069ModelProfiles.resolve(onuTypeName = null, productClass = null))
        assertNull(Tr069ModelProfiles.resolveBuiltin(onuTypeName = "HG8310", productClass = "HG8310"))
    }

    @Test
    fun `buildWanParameterValues includes WAN VLAN without WiFi`() {
        val profile = Tr069ModelProfiles.resolveBuiltin("V2804AX15T", null)!!
        val values = profile.buildWanParameterValues(
            ip = "192.168.123.4",
            subnetMask = "255.255.255.0",
            gateway = "192.168.123.1",
            dns = "8.8.8.8,8.8.4.4",
            vlanId = 1,
        )

        val paths = values.map { it.path }
        assertTrue(paths.any { it.endsWith("ExternalIPAddress") })
        assertTrue(paths.any { it.endsWith("X_CT-COM_VLANIDMark") })
        assertTrue(paths.none { it.contains("WLANConfiguration") })
    }

    @Test
    fun `buildWifiParameterValues includes SSID and KeyPassphrase without WAN`() {
        val profile = Tr069ModelProfiles.resolveBuiltin("V2804AX15T", null)!!
        val values = profile.buildWifiParameterValues(
            wifiSsid24 = "acs2g",
            wifiPassword24 = "11111111",
            wifiSsid5 = "acs5g",
            wifiPassword5 = "11111111",
        )

        val paths = values.map { it.path }
        assertTrue(paths.none { it.contains("WANIPConnection") })
        assertTrue(paths.any { it.endsWith("WLANConfiguration.5.SSID") })
        assertTrue(paths.any { it.endsWith("WLANConfiguration.1.KeyPassphrase") })
        assertEquals("acs2g", values.first { it.path.endsWith("WLANConfiguration.5.SSID") }.value)
    }

    @Test
    fun `buildWifiParameterValues applies security prep before KeyPassphrase for Huawei profile`() {
        val profile = Tr069ModelProfile(
            productClass = "HG8145X6",
            wanIpConnectionPath = "InternetGatewayDevice.WANDevice.1.WANConnectionDevice.1.WANIPConnection.1",
            wlan24Path = "InternetGatewayDevice.LANDevice.1.WLANConfiguration.1",
            wlan5Path = "InternetGatewayDevice.LANDevice.1.WLANConfiguration.5",
            wifiSecurityPrep = Tr069WifiSecurityDefaults.STANDARD_OPEN_WIFI_PREP,
        )
        val values = profile.buildWifiParameterValues(
            wifiSsid24 = "wifi24",
            wifiPassword24 = "pass24",
            wifiSsid5 = "wifi5",
            wifiPassword5 = "pass5",
        )
        val paths = values.map { it.path }

        val key24Index = paths.indexOfFirst { it.endsWith("WLANConfiguration.1.KeyPassphrase") }
        val beacon24Index = paths.indexOfFirst { it.endsWith("WLANConfiguration.1.BeaconType") }
        assertTrue(key24Index > beacon24Index, paths.toString())
        assertEquals("11i", values.first { it.path.endsWith("WLANConfiguration.1.BeaconType") }.value)
    }

    @Test
    fun `withWanConnectionIndex rewrites WAN paths`() {
        val profile = Tr069ModelProfiles.resolveBuiltin("V2804AX15T", null)!!.withWanConnectionIndex(2)
        assertTrue(profile.wanIpConnectionPath.contains("WANConnectionDevice.2.WANIPConnection.1"))
        assertTrue(profile.wanGponLinkConfigPath!!.contains("WANConnectionDevice.2.X_CT-COM_WANGponLinkConfig"))
    }

    @Test
    fun `resolveWanConnectionIndex always uses first available index`() {
        assertEquals(1, Tr069ModelProfiles.resolveWanConnectionIndex(listOf(1, 4, 5)))
        assertEquals(2, Tr069ModelProfiles.resolveWanConnectionIndex(listOf(2, 3)))
        assertEquals(1, Tr069ModelProfiles.resolveWanConnectionIndex(emptyList()))
    }
}
