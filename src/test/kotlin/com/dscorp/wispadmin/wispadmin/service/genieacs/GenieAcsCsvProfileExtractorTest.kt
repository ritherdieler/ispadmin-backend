package com.dscorp.wispadmin.wispadmin.service.genieacs

import org.junit.jupiter.api.Assertions.assertEquals
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
        assertTrue(
            draft.vlanParameters.any {
                it.path.endsWith("X_ZTE-COM_VLANEnable") && it.valueKind == Tr069VlanValueKind.ENABLE_ONE
            },
            draft.vlanParameters.toString(),
        )
        assertTrue(draft.wlan24Path!!.endsWith("WLANConfiguration.5"))
        assertTrue(draft.wlan5Path!!.endsWith("WLANConfiguration.1"))
        assertEquals(null, draft.clientWanIpConnectionPath)
        assertTrue(draft.clientVlanParameters.isEmpty(), draft.clientVlanParameters.toString())
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
        assertEquals(null, draft.clientWanIpConnectionPath)
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
    fun `extracts ZTE F6600R profile from GenieACS CSV export`() {
        val csv = readFixture("genieacs-exports/zte-f6600r.csv")
        val draft = GenieAcsCsvProfileExtractor.extract(csv)

        assertEquals("F6600R", draft.productClass)
        assertTrue(draft.manufacturer!!.contains("ZTE", ignoreCase = true))
        assertTrue(draft.wanIpConnectionPath.contains("WANConnectionDevice.1.WANIPConnection.1"))
        assertTrue(
            draft.vlanParameters.any { it.path.endsWith("X_ZTE-COM_VLANID") },
            draft.vlanParameters.toString(),
        )
        assertTrue(
            draft.vlanParameters.any {
                it.path.endsWith("X_ZTE-COM_VLANEnable") && it.valueKind == Tr069VlanValueKind.ENABLE_TRUE
            },
            draft.vlanParameters.toString(),
        )
        assertTrue(draft.wlan24Path!!.endsWith("WLANConfiguration.1"), draft.wlan24Path)
        assertTrue(draft.wlan5Path!!.endsWith("WLANConfiguration.5"), draft.wlan5Path)
        assertEquals(
            "InternetGatewayDevice.WANDevice.1.WANConnectionDevice.1.WANIPConnection.2",
            draft.clientWanIpConnectionPath,
        )
        assertTrue(
            draft.clientVlanParameters.any {
                it.path.contains("WANDevice.1.WANConnectionDevice.1.WANIPConnection.2") &&
                    it.path.endsWith("X_ZTE-COM_VLANID")
            },
            draft.clientVlanParameters.toString(),
        )
        assertTrue(
            draft.clientVlanParameters.any {
                it.path.contains("WANIPConnection.2") &&
                    it.path.endsWith("X_ZTE-COM_VLANEnable") &&
                    it.valueKind == Tr069VlanValueKind.ENABLE_TRUE
            },
            draft.clientVlanParameters.toString(),
        )
        assertTrue(
            draft.clientVlanParameters.none { it.path.contains("WANDevice.2") },
            draft.clientVlanParameters.toString(),
        )
        assertTrue(draft.wifiSecurityPrep.isNotEmpty(), "Factory open WiFi export should require WPA prep")
        assertTrue(draft.warnings.isEmpty(), draft.warnings.toString())
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

    @Test
    fun `F6600R profile writes boolean VLANEnable and primary WiFi indices`() {
        val csv = readFixture("genieacs-exports/zte-f6600r.csv")
        val profile = GenieAcsCsvProfileExtractor.extract(csv).toModelProfile()
        val values = profile.buildParameterValues(
            ip = "192.168.30.10",
            subnetMask = "255.255.255.0",
            gateway = "192.168.30.1",
            dns = "8.8.8.8",
            vlanId = 200,
            wifiSsid24 = "wifi24",
            wifiPassword24 = "pass24",
            wifiSsid5 = "wifi5",
            wifiPassword5 = "pass5",
        )
        val vlanEnable = values.first { it.path.endsWith("X_ZTE-COM_VLANEnable") }
        assertEquals("true", vlanEnable.value)
        assertEquals("xsd:boolean", vlanEnable.type)
        assertTrue(values.any { it.path.endsWith("X_ZTE-COM_VLANID") && it.value == "200" })
        assertTrue(values.any { it.path.endsWith("WLANConfiguration.1.SSID") && it.value == "wifi24" })
        assertTrue(values.any { it.path.endsWith("WLANConfiguration.5.SSID") && it.value == "wifi5" })
        val clientValues = profile.forClientInternetWan(2).buildClientInternetWanParameterValues(
            ip = "192.168.30.10",
            subnetMask = "255.255.255.0",
            gateway = "192.168.30.1",
            dns = "8.8.8.8",
            vlanId = 200,
            connectionName = "2_INTERNET_R_VID_200",
        )
        assertTrue(
            clientValues.all { it.path.contains("WANDevice.1.WANConnectionDevice.1.WANIPConnection.2") },
            clientValues.map { it.path }.toString(),
        )
        assertTrue(clientValues.none { it.path.contains("WANDevice.2") }, clientValues.map { it.path }.toString())
        assertTrue(clientValues.none { it.path.contains("WANIPConnection.1.") }, clientValues.map { it.path }.toString())
        assertTrue(clientValues.none { it.path.endsWith("X_CT-COM_ServiceList") }, clientValues.map { it.path }.toString())
    }

    private fun readFixture(path: String): String =
        requireNotNull(javaClass.classLoader.getResourceAsStream(path)) { "Missing fixture $path" }
            .bufferedReader()
            .readText()
}
