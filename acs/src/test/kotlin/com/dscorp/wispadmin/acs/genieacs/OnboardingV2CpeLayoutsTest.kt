package com.dscorp.wispadmin.acs.genieacs

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class OnboardingV2CpeLayoutsTest {
    @Test
    fun `Huawei HG8145X6 uses a separate Internet WAN and WLAN 1 and 5`() {
        val layout = OnboardingV2CpeLayouts.of("HG8145X6")

        assertNotNull(layout)
        assertEquals(
            "InternetGatewayDevice.WANDevice.1.WANConnectionDevice.2.WANPPPConnection.1.ExternalIPAddress",
            layout!!.pppExternalIp,
        )
        assertEquals(
            "InternetGatewayDevice.WANDevice.1.WANConnectionDevice.2.WANIPConnection.1.ExternalIPAddress",
            layout.ipExternalIp,
        )
        assertEquals("InternetGatewayDevice.LANDevice.1.WLANConfiguration.1.SSID", layout.ssid24)
        assertEquals("InternetGatewayDevice.LANDevice.1.WLANConfiguration.5.SSID", layout.ssid5)
        assertEquals(null, NamedCpeLayouts.of("HG8145X6"))
    }

    @Test
    fun `existing ZTE and VSOL layouts keep their paths`() {
        assertEquals(
            "InternetGatewayDevice.WANDevice.1.WANConnectionDevice.1.WANPPPConnection.2.ExternalIPAddress",
            OnboardingV2CpeLayouts.of("F6600R")?.pppExternalIp,
        )
        assertEquals(
            "InternetGatewayDevice.LANDevice.1.WLANConfiguration.5.SSID",
            OnboardingV2CpeLayouts.of("V2804AX15T")?.ssid24,
        )
    }

    @Test
    fun `incomplete TP Link exports do not enable unsafe WAN provisioning`() {
        for (productClass in listOf("IGD", "XC220-G3v", "XC220-G3")) {
            assertEquals(null, OnboardingV2CpeLayouts.of(productClass))
        }
    }
}
