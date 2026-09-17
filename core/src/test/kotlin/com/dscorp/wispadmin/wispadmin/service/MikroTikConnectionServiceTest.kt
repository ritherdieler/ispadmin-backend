package com.dscorp.wispadmin.wispadmin.service

import com.dscorp.wispadmin.wispadmin.data.model.NetworkDevice
import com.dscorp.wispadmin.wispadmin.trafficclient.TrafficRouterOsCommandUseCase
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class MikroTikConnectionServiceTest {

    private val routerOsCommands = mockk<TrafficRouterOsCommandUseCase>(relaxed = true)
    private lateinit var service: MikroTikConnectionService

    @BeforeEach
    fun setUp() {
        service = MikroTikConnectionService(routerOsCommands)
    }

    @Test
    fun `printOnDevice delegates to Traffic gateway print`() {
        every {
            routerOsCommands.print(1, "/system/identity", emptyMap(), emptyList())
        } returns Result.success(listOf(mapOf("name" to "MK1")))

        val result = service.printOnDevice(sampleDevice(), "/system/identity")

        assertEquals(listOf(mapOf("name" to "MK1")), result)
        verify(exactly = 1) { routerOsCommands.print(1, "/system/identity", emptyMap(), emptyList()) }
    }

    @Test
    fun `executeCommand maps legacy interface print to REST path`() {
        every {
            routerOsCommands.print(1, "/interface", emptyMap(), emptyList())
        } returns Result.success(listOf(mapOf("name" to "ether1")))

        val result = service.executeCommand(sampleDevice(), "/interface/print")

        assertEquals(1, result.size)
        assertEquals("ether1", result.first()["name"])
    }

    @Test
    fun `callOnDevice delegates to Traffic gateway call`() {
        every {
            routerOsCommands.call(1, "/interface/monitor-traffic", mapOf("interface" to "<pppoe-gf6>", "once" to ""))
        } returns Result.success(listOf(mapOf("tx-bits-per-second" to "8500000", "rx-bits-per-second" to "145000")))

        val result = service.callOnDevice(
            sampleDevice(),
            "/interface/monitor-traffic",
            mapOf("interface" to "<pppoe-gf6>", "once" to ""),
        )

        assertEquals(1, result.size)
        assertEquals("8500000", result.first()["tx-bits-per-second"])
        verify(exactly = 1) {
            routerOsCommands.call(1, "/interface/monitor-traffic", mapOf("interface" to "<pppoe-gf6>", "once" to ""))
        }
    }

    @Test
    fun `setOnDevice delegates to Traffic gateway set`() {
        every {
            routerOsCommands.set(1, "/ip/firewall/address-list", "*9", mapOf("disabled" to "no"))
        } returns Result.success(Unit)

        service.setOnDevice(sampleDevice(), "/ip/firewall/address-list", "*9", mapOf("disabled" to "no"))

        verify(exactly = 1) {
            routerOsCommands.set(1, "/ip/firewall/address-list", "*9", mapOf("disabled" to "no"))
        }
    }

    @Test
    fun `closeConnection does not open RouterOS`() {
        service.closeConnection(8)
        verify(exactly = 0) { routerOsCommands.print(any(), any(), any(), any()) }
    }

    @Test
    fun `isConnectionActive is false for stateless Traffic sessions`() {
        assertFalse(service.isConnectionActive(3))
    }

    @Test
    fun `enableAddressListEntry normalizes id without asterisk`() {
        every {
            routerOsCommands.set(1, "/ip/firewall/address-list", "*19916B", mapOf("disabled" to "no"))
        } returns Result.success(Unit)

        val ok = service.enableAddressListEntry(sampleDevice(), "19916B")

        assertTrue(ok)
        verify(exactly = 1) {
            routerOsCommands.set(1, "/ip/firewall/address-list", "*19916B", mapOf("disabled" to "no"))
        }
    }

    private fun sampleDevice(): NetworkDevice {
        return NetworkDevice(
            id = 1,
            name = "MK1",
            ipAddress = "38.224.231.2",
            username = "admin",
            password = "secret",
            networkDeviceType = NetworkDevice.NetworkDeviceType.CLOUD_CORE_ROUTER
        )
    }
}
