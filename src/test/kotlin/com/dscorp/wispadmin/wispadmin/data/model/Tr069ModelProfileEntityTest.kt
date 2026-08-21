package com.dscorp.wispadmin.wispadmin.data.model

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class Tr069ModelProfileEntityTest {

    private val objectMapper = ObjectMapper()

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
