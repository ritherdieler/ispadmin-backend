package com.dscorp.wispadmin.netdiag.adapter

import com.dscorp.wispadmin.routeros.config.RouterOsClientProperties
import com.dscorp.wispadmin.wispadmin.data.model.NetworkDevice
import com.dscorp.wispadmin.wispadmin.repository.NetworkDeviceRepository
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.util.Optional

class NetDiagDeviceDirectoryAdapterTest {

    private val networkDeviceRepository = mockk<NetworkDeviceRepository>()
    private val routerOsClientProperties = RouterOsClientProperties().apply {
        rest.port = 443
    }
    private val adapter = NetDiagDeviceDirectoryAdapter(networkDeviceRepository, routerOsClientProperties)

    @Test
    fun `retorna null cuando el dispositivo no existe`() {
        every { networkDeviceRepository.findById(42) } returns Optional.empty()

        assertNull(adapter.findMikrotikDeviceRef(42L))
    }

    @Test
    fun `mapea NetworkDevice a MikrotikDeviceRef`() {
        val device = NetworkDevice(
            id = 7,
            name = "MK1",
            password = "secret",
            username = "admin",
            ipAddress = "38.224.231.2"
        )
        every { networkDeviceRepository.findById(7) } returns Optional.of(device)

        val ref = adapter.findMikrotikDeviceRef(7L)

        assertEquals("7", ref?.id)
        assertEquals("38.224.231.2", ref?.host)
        assertEquals(443, ref?.port)
        assertEquals("admin", ref?.username)
        assertEquals("secret", ref?.password)
    }
}
