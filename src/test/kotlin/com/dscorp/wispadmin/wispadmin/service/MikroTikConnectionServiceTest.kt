package com.dscorp.wispadmin.wispadmin.service

import com.dscorp.wispadmin.routeros.config.RouterOsClientProperties
import com.dscorp.wispadmin.routeros.port.MikrotikClient
import com.dscorp.wispadmin.routeros.port.MikrotikDeviceRef
import com.dscorp.wispadmin.routeros.port.MikrotikSession
import com.dscorp.wispadmin.wispadmin.data.model.NetworkDevice
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class MikroTikConnectionServiceTest {

    private val mikrotikClient = mockk<MikrotikClient>(relaxed = true)
    private val properties = RouterOsClientProperties().apply {
        classic.port = 8728
    }

    private lateinit var service: MikroTikConnectionService

    @BeforeEach
    fun setUp() {
        service = MikroTikConnectionService(mikrotikClient, properties)
    }

    @Test
    fun `printOnDevice delegates to MikrotikClient session print`() {
        val deviceRefSlot = slot<MikrotikDeviceRef>()
        val session = mockk<MikrotikSession>()
        every { session.print("/system/identity", emptyMap()) } returns listOf(mapOf("name" to "MK1"))
        every {
            mikrotikClient.withSession(capture(deviceRefSlot), any<(MikrotikSession) -> List<Map<String, String>>>())
        } answers {
            val block = arg<(MikrotikSession) -> List<Map<String, String>>>(1)
            block(session)
        }

        val result = service.printOnDevice(sampleDevice(), "/system/identity")

        assertEquals(listOf(mapOf("name" to "MK1")), result)
        assertEquals("1", deviceRefSlot.captured.id)
        assertEquals(8728, deviceRefSlot.captured.port)
        verify(exactly = 1) { session.print("/system/identity", emptyMap()) }
    }

    @Test
    fun `executeCommand maps legacy interface print to REST path`() {
        val session = mockk<MikrotikSession>()
        every { session.print("/interface", emptyMap()) } returns listOf(mapOf("name" to "ether1"))
        every {
            mikrotikClient.withSession(any(), any<(MikrotikSession) -> List<Map<String, String>>>())
        } answers {
            val block = arg<(MikrotikSession) -> List<Map<String, String>>>(1)
            block(session)
        }

        val result = service.executeCommand(sampleDevice(), "/interface/print")

        assertEquals(1, result.size)
        assertEquals("ether1", result.first()["name"])
    }

    @Test
    fun `setOnDevice delegates to session set`() {
        val session = mockk<MikrotikSession>(relaxed = true)
        every {
            mikrotikClient.withSession(any(), any<(MikrotikSession) -> Unit>())
        } answers {
            val block = arg<(MikrotikSession) -> Unit>(1)
            block(session)
        }

        service.setOnDevice(sampleDevice(), "/ip/firewall/address-list", "*9", mapOf("disabled" to "no"))

        verify(exactly = 1) {
            session.set("/ip/firewall/address-list", "*9", mapOf("disabled" to "no"))
        }
    }

    @Test
    fun `closeConnection delegates to MikrotikClient`() {
        service.closeConnection(8)
        verify(exactly = 1) { mikrotikClient.closeSession("8") }
    }

    @Test
    fun `isConnectionActive delegates to MikrotikClient`() {
        every { mikrotikClient.isSessionActive("3") } returns true
        assertTrue(service.isConnectionActive(3))
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
