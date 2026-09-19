package com.dscorp.wispadmin.oltgateway.service

import com.dscorp.wispadmin.oltgateway.config.OltGatewayProperties
import com.dscorp.wispadmin.oltgateway.port.OltInventoryPort
import com.dscorp.wispadmin.oltgateway.port.OltOnuSnapshot
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OltServicePortServiceTest {

    private val inventory = mockk<OltInventoryPort>()
    private val commands = mockk<OltGatewayCommandService>()
    private val properties = OltGatewayProperties().apply {
        writes.labAcsLineProfileId = 12
        writes.labAcsMgmtVlan = 1000
        writes.labAcsMgmtGemport = 2
    }
    private val service = OltServicePortService(inventory, commands, properties)

    private fun onu(): OltOnuSnapshot = OltOnuSnapshot(
        id = 1,
        sn = "VSOL0031C0B6",
        externalId = "ext-1",
        oltId = 2,
        oltName = "MA5608T",
        board = 1,
        port = 6,
        onuIndex = 116,
    )

    @Test
    fun `ensureMgmtVlan no escribe si 1000 ya figura`() {
        every { inventory.findBySn("VSOL0031C0B6") } returns onu()
        every { commands.displayServicePorts(1, 6, 116) } returns
            "service-port 18 vlan 100\nservice-port 1857 vlan 1000\n"
        every { commands.parseServicePortVlans(any()) } returns setOf(100, 1000)

        val result = service.ensureMgmtVlan("VSOL0031C0B6")

        assertEquals(setOf(100, 1000), result.vlans)
        verify(exactly = 0) { commands.ensureMgmtServicePort(any()) }
    }

    @Test
    fun `ensureMgmtVlan pide lineprofile 12 y SP 1000 gem 2`() {
        every { inventory.findBySn("12345B4641531C0B6") } returns onu()
        every { commands.displayServicePorts(1, 6, 116) } returns "service-port 18 vlan 100\n"
        every { commands.parseServicePortVlans(any()) } returns setOf(100)
        every { commands.ensureMgmtServicePort(any()) } returns setOf(100, 1000)

        val result = service.ensureMgmtVlan("12345B4641531C0B6")

        assertEquals(setOf(100, 1000), result.vlans)
        verify {
            commands.ensureMgmtServicePort(
                EnsureMgmtServicePortRequest(
                    board = 1,
                    port = 6,
                    ontId = 116,
                    vlan = 1000,
                    gemport = 2,
                    lineProfileId = 12,
                ),
            )
        }
        assertTrue(result.sn == "VSOL0031C0B6")
    }
}
