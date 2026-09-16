package com.dscorp.wispadmin.wispadmin.service

import com.dscorp.wispadmin.wispadmin.data.model.NetworkDevice
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NetworkDeviceConnectionServiceTest {

    private val mikrotikConnectionService = mockk<MikroTikConnectionService>()
    private val service = NetworkDeviceConnectionService(mikrotikConnectionService)
    private val device = NetworkDevice(
        id = 8,
        name = "MK2",
        networkDeviceType = NetworkDevice.NetworkDeviceType.CLOUD_CORE_ROUTER,
    )

    @Test
    fun `getDeviceInterfaces includes pppoe-in sessions`() {
        every { mikrotikConnectionService.printOnDevice(device, "/interface") } returns listOf(
            mapOf("name" to "ether1", "type" to "ether", "rx-byte" to "10", "tx-byte" to "4"),
            mapOf("name" to "<pppoe-gf6>", "type" to "pppoe-in", "rx-byte" to "2147483648", "tx-byte" to "152043520"),
        )

        val interfaces = service.getDeviceInterfaces(device)

        assertEquals(2, interfaces.size)
        val pppoe = interfaces.single { it["type"] == "pppoe-in" }
        assertEquals("<pppoe-gf6>", pppoe["name"])
    }

    @Test
    fun `getDeviceInterfaces includes rxBytes and txBytes`() {
        every { mikrotikConnectionService.printOnDevice(device, "/interface") } returns listOf(
            mapOf("name" to "<pppoe-gf6>", "type" to "pppoe-in", "rx-byte" to "2147483648", "tx-byte" to "152043520"),
        )

        val interfaces = service.getDeviceInterfaces(device)

        assertEquals("2147483648", interfaces.single()["rxBytes"])
        assertEquals("152043520", interfaces.single()["txBytes"])
    }

    @Test
    fun `getDeviceInterfaces keeps existing metadata fields`() {
        every { mikrotikConnectionService.printOnDevice(device, "/interface") } returns listOf(
            mapOf(
                "name" to "ether1",
                "type" to "ether",
                "mtu" to "1500",
                "mac-address" to "AA:BB:CC:DD:EE:01",
                "running" to "true",
                "disabled" to "false",
                "comment" to "wan",
                "rx-byte" to "10",
                "tx-byte" to "4",
            ),
        )

        val iface = service.getDeviceInterfaces(device).single()

        assertEquals("1500", iface["mtu"])
        assertEquals("AA:BB:CC:DD:EE:01", iface["macAddress"])
        assertTrue(iface.containsKey("rxBytes"))
        assertTrue(iface.containsKey("txBytes"))
    }
}
