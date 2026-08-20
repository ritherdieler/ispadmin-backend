package com.dscorp.wispadmin.wispadmin.service.genieacs

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class Tr069ModelProfileTest {

    @Test
    fun `resolves VSOL V2804AX15T from onu type name`() {
        val profile = Tr069ModelProfiles.resolve(onuTypeName = "V2804AX15T", productClass = null)
        assertNotNull(profile)
        assertEquals("V2804AX15T", profile!!.productClass)
        assertTrue(profile.wanIpConnectionPath.contains("WANConnectionDevice.4.WANIPConnection.1"))
        assertTrue(profile.wlan24Path.endsWith("WLANConfiguration.5"))
        assertTrue(profile.wlan5Path.endsWith("WLANConfiguration.1"))
    }

    @Test
    fun `resolves VSOL profile from product class`() {
        val profile = Tr069ModelProfiles.resolve(onuTypeName = "unknown", productClass = "V2804AX15T")
        assertNotNull(profile)
    }

    @Test
    fun `returns null for unknown model`() {
        assertNull(Tr069ModelProfiles.resolve(onuTypeName = "HG8310", productClass = "HG8310"))
        assertNull(Tr069ModelProfiles.resolve(onuTypeName = null, productClass = null))
    }

    @Test
    fun `buildParameterValues includes WAN VLAN and WiFi paths`() {
        val profile = Tr069ModelProfiles.resolve("V2804AX15T", null)!!
        val values = profile.buildParameterValues(
            ip = "192.168.123.4",
            subnetMask = "255.255.255.0",
            gateway = "192.168.123.1",
            dns = "8.8.8.8,8.8.4.4",
            vlanId = 1,
            wifiSsid24 = "acs2g",
            wifiPassword24 = "11111111",
            wifiSsid5 = "acs5g",
            wifiPassword5 = "11111111",
        )

        val paths = values.map { it.path }
        assertTrue(paths.any { it.endsWith("ExternalIPAddress") })
        assertTrue(paths.any { it.endsWith("X_CT-COM_VLANIDMark") })
        assertTrue(paths.any { it.endsWith("WLANConfiguration.5.SSID") })
        assertTrue(paths.any { it.endsWith("WLANConfiguration.1.KeyPassphrase") })
        assertEquals("192.168.123.4", values.first { it.path.endsWith("ExternalIPAddress") }.value)
        assertEquals("acs2g", values.first { it.path.endsWith("WLANConfiguration.5.SSID") }.value)
    }
}
