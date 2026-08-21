package com.dscorp.wispadmin.wispadmin.service.genieacs

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GenieAcsCsvProfileExtractorTest {

    @Test
    fun `extracts VSOL V2804AX15T profile from GenieACS CSV export`() {
        val csv = readFixture("genieacs-exports/vsol-v2804ax15t.csv")
        val draft = GenieAcsCsvProfileExtractor.extract(csv)

        assertEquals("V2804AX15T", draft.productClass)
        assertEquals("Realtek", draft.manufacturer)
        assertTrue(draft.wanIpConnectionPath.contains("WANConnectionDevice.1.WANIPConnection.1"))
        assertTrue(
            draft.vlanParameters.any { it.path.endsWith("X_CT-COM_VLANIDMark") },
            draft.vlanParameters.toString(),
        )
        assertTrue(
            draft.vlanParameters.any { it.path.endsWith("X_ZTE-COM_VLANID") },
            draft.vlanParameters.toString(),
        )
        assertTrue(draft.wlan24Path!!.endsWith("WLANConfiguration.5"))
        assertTrue(draft.wlan5Path!!.endsWith("WLANConfiguration.1"))
        assertTrue(draft.warnings.isEmpty(), draft.warnings.toString())
    }

    @Test
    fun `extracts Huawei HG8145X6 profile from GenieACS CSV export`() {
        val csv = readFixture("genieacs-exports/huawei-hg8145x6.csv")
        val draft = GenieAcsCsvProfileExtractor.extract(csv)

        assertEquals("HG8145X6", draft.productClass)
        assertTrue(draft.manufacturer!!.contains("Huawei", ignoreCase = true))
        assertTrue(draft.wanIpConnectionPath.contains("WANConnectionDevice.1.WANIPConnection.1"))
        assertTrue(
            draft.vlanParameters.any { it.path.endsWith("X_HW_VLAN") && it.path.contains("WANIPConnection") },
            draft.vlanParameters.toString(),
        )
        assertTrue(draft.wlan24Path!!.endsWith("WLANConfiguration.1"))
        assertTrue(draft.wlan5Path!!.endsWith("WLANConfiguration.5"))
        assertTrue(draft.wifiSecurityPrep.isNotEmpty(), "Factory open WiFi export should require WPA prep")
        assertTrue(
            draft.wifiSecurityPrep.any { it.parameterSuffix == "BeaconType" && it.value == "11i" },
            draft.wifiSecurityPrep.toString(),
        )
    }

    @Test
    fun `VSOL export does not require wifi security prep`() {
        val csv = readFixture("genieacs-exports/vsol-v2804ax15t.csv")
        val draft = GenieAcsCsvProfileExtractor.extract(csv)
        assertTrue(draft.wifiSecurityPrep.isEmpty(), draft.wifiSecurityPrep.toString())
    }

    @Test
    fun `toModelProfile builds usable Tr069ModelProfile`() {
        val csv = readFixture("genieacs-exports/huawei-hg8145x6.csv")
        val profile = GenieAcsCsvProfileExtractor.extract(csv).toModelProfile()
        val values = profile.buildParameterValues(
            ip = "192.168.30.10",
            subnetMask = "255.255.255.0",
            gateway = "192.168.30.1",
            dns = "8.8.8.8",
            vlanId = 100,
            wifiSsid24 = "wifi24",
            wifiPassword24 = "pass24",
            wifiSsid5 = "wifi5",
            wifiPassword5 = "pass5",
        )
        assertTrue(values.any { it.path.endsWith("ExternalIPAddress") && it.value == "192.168.30.10" })
        assertTrue(values.any { it.path.endsWith("X_HW_VLAN") && it.value == "100" })
        assertTrue(values.any { it.path.endsWith("WLANConfiguration.1.SSID") && it.value == "wifi24" })
        assertTrue(values.any { it.path.endsWith("WLANConfiguration.5.SSID") && it.value == "wifi5" })
    }

    private fun readFixture(path: String): String =
        requireNotNull(javaClass.classLoader.getResourceAsStream(path)) { "Missing fixture $path" }
            .bufferedReader()
            .readText()
}
