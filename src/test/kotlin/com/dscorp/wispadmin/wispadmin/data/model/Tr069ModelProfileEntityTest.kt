package com.dscorp.wispadmin.wispadmin.data.model

import com.dscorp.wispadmin.wispadmin.service.genieacs.Tr069VlanValueKind
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class Tr069ModelProfileEntityTest {

    private val objectMapper = ObjectMapper()

    @Test
    fun `toModelProfile maps client WAN path and vlan parameters`() {
        val entity = Tr069ModelProfileEntity(
            productClass = "F6600R",
            wanIpConnectionPath = "InternetGatewayDevice.WANDevice.1.WANConnectionDevice.1.WANIPConnection.1",
            clientWanIpConnectionPath = "InternetGatewayDevice.WANDevice.1.WANConnectionDevice.1.WANIPConnection.2",
            vlanParametersJson = "[]",
            clientVlanParametersJson = """[{"path":"InternetGatewayDevice.WANDevice.1.WANConnectionDevice.1.WANIPConnection.2.X_ZTE-COM_VLANID","valueKind":"VLAN_ID"}]""",
            wifiSecurityPrepJson = "[]",
        )

        val profile = entity.toModelProfile(objectMapper)

        assertEquals(
            "InternetGatewayDevice.WANDevice.1.WANConnectionDevice.1.WANIPConnection.2",
            profile.clientWanIpConnectionPath,
        )
        assertEquals(1, profile.clientVlanParameters.size)
        assertEquals(Tr069VlanValueKind.VLAN_ID, profile.clientVlanParameters.first().valueKind)
    }

    @Test
    fun `toModelProfile does not infer wifiSecurityPrep when json is empty`() {
        val entity = Tr069ModelProfileEntity(
            productClass = "HG8145X6",
            manufacturer = "Huawei Technologies Co., Ltd",
            wanIpConnectionPath = "InternetGatewayDevice.WANDevice.1.WANConnectionDevice.1.WANIPConnection.1",
            vlanParametersJson = "[]",
            wlan24Path = "InternetGatewayDevice.LANDevice.1.WLANConfiguration.1",
            wlan5Path = "InternetGatewayDevice.LANDevice.1.WLANConfiguration.5",
            wifiSecurityPrepJson = "[]",
        )

        val profile = entity.toModelProfile(objectMapper)

        assertTrue(profile.wifiSecurityPrep.isEmpty())
    }
}
