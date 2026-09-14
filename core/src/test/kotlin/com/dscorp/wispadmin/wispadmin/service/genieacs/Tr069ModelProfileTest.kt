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
    fun `Huawei client WAN uses X_HW_SERVICELIST LANBIND and no ZTE leaves`() {
        val client = huaweiHg8145Profile().forClientInternetWan(2)
        val values = client.buildClientInternetWanParameterValues(
            ip = "192.168.30.250",
            subnetMask = "255.255.255.0",
            gateway = "192.168.30.1",
            dns = "8.8.8.8,8.8.4.4",
            vlanId = 100,
            connectionName = "2_INTERNET_R_VID_100",
        )
        val paths = values.map { it.path }

        assertTrue(values.all { it.path.contains("WANConnectionDevice.2.") }, paths.toString())
        assertEquals("INTERNET", values.first { it.path.endsWith("X_HW_SERVICELIST") }.value)
        assertEquals("AlwaysOn", values.first { it.path.endsWith("ConnectionTrigger") }.value)
        assertEquals("true", values.first { it.path.endsWith("X_HW_IPv4Enable") }.value)
        assertEquals("100", values.first { it.path.endsWith("X_HW_VLAN") }.value)
        assertEquals("xsd:unsignedInt", values.first { it.path.endsWith("X_HW_VLAN") }.type)
        assertTrue(values.none { it.path.contains("X_ZTE-COM_") }, paths.toString())
        assertTrue(values.none { it.path.contains("X_CT-COM_") }, paths.toString())
        assertTrue(values.none { it.path.contains("WLANConfiguration") }, paths.toString())
        listOf("Lan1Enable", "Lan4Enable", "SSID1Enable", "SSID4Enable").forEach { leaf ->
            val param = values.first { it.path.endsWith("X_HW_LANBIND.$leaf") }
            assertEquals("1", param.value)
            assertEquals("xsd:unsignedInt", param.type)
        }
        assertTrue(client.usesHuaweiWanExtensions())
        val isolated = client.buildIsolatedL3ParameterValues(
            subnetMask = "255.255.255.0",
            dns = "8.8.8.8,8.8.4.4",
        )
        assertEquals(
            listOf("NATEnabled", "DNSServers", "DNSEnabled", "SubnetMask"),
            isolated.map { it.path.substringAfterLast('.') },
        )
    }

    @Test
    fun `Huawei wifi SPV is SSID and PSK only without BeaconType`() {
        val profile = Tr069ModelProfile(
            productClass = "HG8145X6",
            wanIpConnectionPath = "InternetGatewayDevice.WANDevice.1.WANConnectionDevice.1.WANIPConnection.1",
            wlan24Path = "InternetGatewayDevice.LANDevice.1.WLANConfiguration.1",
            wlan5Path = "InternetGatewayDevice.LANDevice.1.WLANConfiguration.5",
            vlanParameters = listOf(
                Tr069VlanParameterSpec(
                    path = "InternetGatewayDevice.WANDevice.1.WANConnectionDevice.1.WANIPConnection.1.X_HW_VLAN",
                ),
            ),
            wifiSecurityPrep = Tr069WifiSecurityDefaults.STANDARD_OPEN_WIFI_PREP,
        )
        val values = profile.buildWifiParameterValues(
            wifiSsid24 = "wifi24",
            wifiPassword24 = "pass24",
            wifiSsid5 = "wifi5",
            wifiPassword5 = "pass5",
        )
        val paths = values.map { it.path }
        assertTrue(paths.none { it.endsWith("BeaconType") }, paths.toString())
        assertTrue(paths.any { it.endsWith("WLANConfiguration.1.SSID") })
        assertTrue(paths.any { it.endsWith("WLANConfiguration.1.PreSharedKey.1.KeyPassphrase") }, paths.toString())
        assertTrue(paths.none { it.endsWith("WLANConfiguration.1.KeyPassphrase") }, paths.toString())
        assertEquals("wifi24", values.first { it.path.endsWith("WLANConfiguration.1.SSID") }.value)
        assertEquals(
            "pass24",
            values.first { it.path.endsWith("WLANConfiguration.1.PreSharedKey.1.KeyPassphrase") }.value,
        )
    }

    @Test
    fun `withWanConnectionIndex rewrites WAN paths`() {
        val profile = Tr069ModelProfiles.resolveBuiltin("V2804AX15T", null)!!.withWanConnectionIndex(2)
        assertTrue(profile.wanIpConnectionPath.contains("WANConnectionDevice.2.WANIPConnection.1"))
        assertTrue(profile.wanGponLinkConfigPath!!.contains("WANConnectionDevice.2.X_CT-COM_WANGponLinkConfig"))
    }

    @Test
    fun `forClientInternetWan keeps VSOL dual WCD rewrite when client path is null`() {
        val profile = Tr069ModelProfiles.resolveBuiltin("V2804AX15T", null)!!
        val client = profile.forClientInternetWan(2)
        assertEquals(profile.withWanConnectionIndex(2), client)
        assertTrue(client.wanIpConnectionPath.contains("WANDevice.1.WANConnectionDevice.2"))
    }

    @Test
    fun `forClientInternetWan uses sibling WANIPConnection 2 on the same GPON WCD`() {
        val profile = Tr069ModelProfile(
            productClass = "F6600R",
            wanIpConnectionPath = "InternetGatewayDevice.WANDevice.1.WANConnectionDevice.1.WANIPConnection.1",
            wlan24Path = "InternetGatewayDevice.LANDevice.1.WLANConfiguration.1",
            wlan5Path = "InternetGatewayDevice.LANDevice.1.WLANConfiguration.5",
            clientWanIpConnectionPath = "InternetGatewayDevice.WANDevice.1.WANConnectionDevice.1.WANIPConnection.2",
            clientVlanParameters = listOf(
                Tr069VlanParameterSpec(
                    path = "InternetGatewayDevice.WANDevice.1.WANConnectionDevice.1.WANIPConnection.2.X_ZTE-COM_VLANID",
                ),
                Tr069VlanParameterSpec(
                    path = "InternetGatewayDevice.WANDevice.1.WANConnectionDevice.1.WANIPConnection.2.X_ZTE-COM_VLANEnable",
                    valueKind = Tr069VlanValueKind.ENABLE_TRUE,
                ),
            ),
        )
        val client = profile.forClientInternetWan(2)
        assertEquals(
            "InternetGatewayDevice.WANDevice.1.WANConnectionDevice.1.WANIPConnection.2",
            client.wanIpConnectionPath,
        )
        assertEquals(1, client.wanConnectionDeviceIndex)
        assertEquals(2, client.wanIpInstanceIndex())
        assertEquals("InternetGatewayDevice.WANDevice.1.WANConnectionDevice", client.wcdParentPath())
        val values = client.buildClientInternetWanParameterValues(
            ip = "192.168.30.10",
            subnetMask = "255.255.255.0",
            gateway = "192.168.30.1",
            dns = "8.8.8.8",
            vlanId = 100,
            connectionName = "2_INTERNET_R_VID_100",
        )
        assertTrue(
            values.all { it.path.contains("WANDevice.1.WANConnectionDevice.1.WANIPConnection.2") },
            values.map { it.path }.toString(),
        )
        assertTrue(values.none { it.path.contains("WANDevice.2") }, values.map { it.path }.toString())
        assertTrue(values.none { it.path.contains("WANConnectionDevice.2") }, values.map { it.path }.toString())
        assertTrue(values.none { it.path.endsWith("X_CT-COM_ServiceList") }, values.map { it.path }.toString())
        assertEquals("INTERNET", values.first { it.path.endsWith("X_ZTE-COM_ServiceList") }.value)
        assertEquals("true", values.first { it.path.endsWith("X_ZTE-COM_VLANEnable") }.value)
        assertEquals("xsd:boolean", values.first { it.path.endsWith("X_ZTE-COM_VLANEnable") }.type)
    }

    @Test
    fun `resolveWanConnectionIndex always uses first available index`() {
        assertEquals(1, Tr069ModelProfiles.resolveWanConnectionIndex(listOf(1, 4, 5)))
        assertEquals(2, Tr069ModelProfiles.resolveWanConnectionIndex(listOf(2, 3)))
        assertEquals(1, Tr069ModelProfiles.resolveWanConnectionIndex(emptyList()))
    }

    @Test
    fun `buildClientInternetWanParameterValues targets WCD2 Static INTERNET without WiFi`() {
        val profile = Tr069ModelProfiles.resolveBuiltin("V2804AX15T", null)!!
            .withWanConnectionIndex(Tr069ModelProfiles.CLIENT_WAN_INDEX)
        val values = profile.buildClientInternetWanParameterValues(
            ip = "192.168.30.216",
            subnetMask = "255.255.255.0",
            gateway = "192.168.30.1",
            dns = "8.8.8.8,8.8.4.4",
            vlanId = 100,
            connectionName = "2_INTERNET_R_VID_100",
        )
        val bySuffix = values.associate { it.path.substringAfterLast('.') to it }

        assertTrue(values.all { it.path.contains("WANConnectionDevice.2.") }, values.map { it.path }.toString())
        assertTrue(values.none { it.path.contains("WANConnectionDevice.1.") }, values.map { it.path }.toString())
        assertTrue(values.none { it.path.contains("WLANConfiguration") })
        assertEquals("true", bySuffix.getValue("Enable").value)
        assertEquals("xsd:boolean", bySuffix.getValue("Enable").type)
        assertEquals("IP_Routed", bySuffix.getValue("ConnectionType").value)
        assertEquals("2_INTERNET_R_VID_100", bySuffix.getValue("Name").value)
        assertEquals("INTERNET", values.first { it.path.endsWith("X_CT-COM_ServiceList") }.value)
        assertEquals("INTERNET", values.first { it.path.endsWith("X_ZTE-COM_ServiceList") }.value)
        assertEquals("true", bySuffix.getValue("NATEnabled").value)
        assertEquals("Static", bySuffix.getValue("AddressingType").value)
        assertEquals("192.168.30.216", bySuffix.getValue("ExternalIPAddress").value)
        assertEquals("255.255.255.0", bySuffix.getValue("SubnetMask").value)
        assertEquals("192.168.30.1", bySuffix.getValue("DefaultGateway").value)
        assertEquals("8.8.8.8,8.8.4.4", bySuffix.getValue("DNSServers").value)
        assertEquals("true", bySuffix.getValue("DNSEnabled").value)
        assertEquals("100", values.first { it.path.endsWith("X_CT-COM_VLANIDMark") }.value)
        assertEquals("100", values.first { it.path.endsWith("X_ZTE-COM_VLANID") }.value)
        assertEquals("1", values.first { it.path.endsWith("X_ZTE-COM_VLANEnable") }.value)
        assertTrue(values.any { it.path.contains("WANGponLinkConfig") && it.path.endsWith("VLANIDMark") })
    }

    @Test
    fun `buildIdentityNameParameterValues only sets WAN Name`() {
        val profile = Tr069ModelProfiles.resolveBuiltin("V2804AX15T", null)!!
            .withWanConnectionIndex(Tr069ModelProfiles.CLIENT_WAN_INDEX)
        val values = profile.buildIdentityNameParameterValues("744 TV JUAN PEREZ")
        assertEquals(2, values.size)
        assertEquals("744 TV JUAN PEREZ", values.first { it.path.endsWith(".Name") }.value)
        assertEquals("744 TV JUAN PEREZ", values.first { it.path.endsWith(".Alias") }.value)
        assertTrue(values.all { it.path.contains("WANConnectionDevice.2.") })
        assertTrue(values.all { it.type == "xsd:string" })
        assertTrue(values.none { it.path.contains("ExternalIPAddress") })
    }

    @Test
    fun `buildStagingDhcpParameterValues sets DHCP and VLAN without static IP or WiFi`() {
        val profile = Tr069ModelProfiles.resolveBuiltin("V2804AX15T", null)!!
        val values = profile.buildStagingDhcpParameterValues(vlanId = 100)

        val paths = values.map { it.path }
        assertEquals("DHCP", values.first { it.path.endsWith("AddressingType") }.value)
        assertTrue(paths.any { it.endsWith("X_CT-COM_VLANIDMark") })
        assertTrue(paths.none { it.endsWith("ExternalIPAddress") })
        assertTrue(paths.none { it.contains("WLANConfiguration") })
    }

    @Test
    fun `buildParameterValues merges WAN and WiFi in monolithic order`() {
        val profile = Tr069ModelProfiles.resolveBuiltin("V2804AX15T", null)!!
        val values = profile.buildParameterValues(
            ip = "192.168.30.215",
            subnetMask = "255.255.255.0",
            gateway = "192.168.30.1",
            dns = "8.8.8.8,8.8.4.4",
            vlanId = 100,
            wifiSsid24 = "puppy",
            wifiPassword24 = "qqqqqqqq",
            wifiSsid5 = "bdbdbxbd",
            wifiPassword5 = "bdbdbdjxxj",
        )
        val paths = values.map { it.path }

        val wanEnd = paths.indexOfLast { it.contains("WANIPConnection") || it.contains("WANGponLinkConfig") }
        val wifiStart = paths.indexOfFirst { it.contains("WLANConfiguration") }
        assertTrue(wanEnd < wifiStart, "WAN params must precede WiFi in monolithic SPV: $paths")

        val vlanIdIndex = paths.indexOfFirst { it.endsWith("X_ZTE-COM_VLANID") }
        val vlanEnableIndex = paths.indexOfFirst { it.endsWith("X_ZTE-COM_VLANEnable") }
        assertTrue(vlanIdIndex < vlanEnableIndex, "VLANID before VLANEnable: $paths")
    }

    private fun huaweiHg8145Profile() = Tr069ModelProfile(
        productClass = "HG8145X6",
        wanIpConnectionPath = "InternetGatewayDevice.WANDevice.1.WANConnectionDevice.1.WANIPConnection.1",
        wlan24Path = "InternetGatewayDevice.LANDevice.1.WLANConfiguration.1",
        wlan5Path = "InternetGatewayDevice.LANDevice.1.WLANConfiguration.5",
        vlanParameters = listOf(
            Tr069VlanParameterSpec(
                path = "InternetGatewayDevice.WANDevice.1.WANConnectionDevice.1.WANIPConnection.1.X_HW_VLAN",
            ),
        ),
        wifiSecurityPrep = Tr069WifiSecurityDefaults.STANDARD_OPEN_WIFI_PREP,
    )
}
