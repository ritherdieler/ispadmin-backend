package com.dscorp.wispadmin.wispadmin.data.model

import com.dscorp.wispadmin.wispadmin.mapper.toDto
import com.dscorp.wispadmin.wispadmin.requestbody.NetworkDeviceRequest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class NetworkDeviceVlanTest {

    @Test
    fun `toDto incluye vlanId cuando esta configurado en CLOUD_CORE_ROUTER`() {
        val device = NetworkDevice(
            id = 8,
            name = "MK2",
            ipAddress = "38.224.231.4",
            networkDeviceType = NetworkDevice.NetworkDeviceType.CLOUD_CORE_ROUTER,
            vlanId = 100
        )

        val dto = device.toDto()

        assertEquals(100, dto.vlanId)
        assertEquals(NetworkDevice.NetworkDeviceType.CLOUD_CORE_ROUTER, dto.networkDeviceType)
    }

    @Test
    fun `toDto devuelve vlanId null para dispositivos sin vlan configurada`() {
        val device = NetworkDevice(
            id = 2,
            name = "CPE",
            networkDeviceType = NetworkDevice.NetworkDeviceType.FIBER_ROUTER
        )

        assertNull(device.toDto().vlanId)
    }

    @Test
    fun `NetworkDeviceRequest mapea vlanId al modelo`() {
        val request = NetworkDeviceRequest(
            id = 1,
            name = "MK1",
            ipAddress = "38.224.231.2",
            networkDeviceType = NetworkDevice.NetworkDeviceType.CLOUD_CORE_ROUTER,
            vlanId = 1
        )

        val model = request.toModel()

        assertEquals(1, model.vlanId)
        assertEquals(NetworkDevice.NetworkDeviceType.CLOUD_CORE_ROUTER, model.networkDeviceType)
    }
}
