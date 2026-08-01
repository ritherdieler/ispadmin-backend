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
    fun `executeSingleCommand delegates to MikrotikClient session execute`() {
        val deviceRefSlot = slot<MikrotikDeviceRef>()
        val session = mockk<MikrotikSession>()
        every { session.execute("/system/identity/print") } returns listOf(mapOf("name" to "MK1"))
        every {
            mikrotikClient.withSession(capture(deviceRefSlot), any<(MikrotikSession) -> List<Map<String, String>>>())
        } answers {
            val block = arg<(MikrotikSession) -> List<Map<String, String>>>(1)
            block(session)
        }

        val result = service.executeSingleCommand(sampleDevice(), "/system/identity/print")

        assertEquals(listOf(mapOf("name" to "MK1")), result)
        assertEquals("1", deviceRefSlot.captured.id)
        assertEquals("38.224.231.2", deviceRefSlot.captured.host)
        assertEquals(8728, deviceRefSlot.captured.port)
        assertEquals("admin", deviceRefSlot.captured.username)
        assertEquals("secret", deviceRefSlot.captured.password)
        verify(exactly = 1) { session.execute("/system/identity/print") }
    }

    @Test
    fun `executeCommand delegates to MikrotikClient session execute`() {
        val session = mockk<MikrotikSession>()
        every { session.execute("/interface/print") } returns listOf(mapOf("name" to "ether1"))
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
    fun `closeConnection delegates to MikrotikClient`() {
        service.closeConnection(8)
        verify(exactly = 1) { mikrotikClient.closeSession("8") }
    }

    @Test
    fun `isConnectionActive delegates to MikrotikClient`() {
        every { mikrotikClient.isSessionActive("3") } returns true
        assertTrue(service.isConnectionActive(3))
    }

    @Test
    fun `enableAddressListEntry normalizes id without asterisk`() {
        val session = mockk<MikrotikSession>()
        val commandSlot = slot<String>()
        every { session.execute(capture(commandSlot)) } returns emptyList()
        every {
            mikrotikClient.withSession(any(), any<(MikrotikSession) -> List<Map<String, String>>>())
        } answers {
            val block = arg<(MikrotikSession) -> List<Map<String, String>>>(1)
            block(session)
        }

        val ok = service.enableAddressListEntry(sampleDevice(), "19916B")

        assertTrue(ok)
        assertTrue(commandSlot.captured.contains(".id=*19916B"))
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
