package com.dscorp.wispadmin.wispadmin.data.model

import com.dscorp.wispadmin.wispadmin.requestbody.NetworkDeviceRequest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NetworkDeviceDisabledTest {

    @Test
    fun `NetworkDevice no deshabilitado por defecto`() {
        val device = NetworkDevice(id = 1, name = "MK1")

        assertFalse(device.disabled)
        assertFalse(device.toDto().disabled)
    }

    @Test
    fun `toDto expone disabled true cuando el dispositivo esta deshabilitado`() {
        val device = NetworkDevice(
            id = 8,
            name = "MK2",
            disabled = true
        )

        assertTrue(device.toDto().disabled)
    }

    @Test
    fun `NetworkDeviceRequest mapea disabled al modelo`() {
        val request = NetworkDeviceRequest(
            id = 8,
            name = "MK2",
            disabled = true
        )

        val model = request.toModel()

        assertTrue(model.disabled)
    }
}
