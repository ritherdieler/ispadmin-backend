package com.dscorp.wispadmin.oltgateway.service

import com.dscorp.wispadmin.oltgateway.config.OnuWriteProvider
import com.dscorp.wispadmin.oltgateway.config.OnuWriteProviderProperties
import com.dscorp.wispadmin.oltgateway.smartolt.SmartOltAuthorizeCommand
import com.dscorp.wispadmin.oltgateway.smartolt.SmartOltMoveCommand
import com.dscorp.wispadmin.oltgateway.smartolt.SmartOltWriteClient
import com.dscorp.wispadmin.oltgateway.smartolt.SmartOltWriteResult
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class OnuWriteRouterTest {

    private val commandService = mockk<OltGatewayCommandService>(relaxed = true)
    private val smartOlt = mockk<SmartOltWriteClient>(relaxed = true)
    private val props = OnuWriteProviderProperties()

    private fun router() = OnuWriteRouter(props, commandService, smartOlt)

    private val sshAuthorize = AuthorizeCliRequest(
        board = 1,
        port = 6,
        ontId = 16,
        sn = "ZTEGDC47BFFD",
        lineProfileId = 10,
        serviceProfileId = 10,
        description = "lab",
        vlan = 100,
    )
    private val cloudAuthorize = SmartOltAuthorizeCommand(
        oltId = "gigafiber-ma5608t",
        ponType = "gpon",
        board = "1",
        port = "6",
        sn = "ZTEGDC47BFFD",
        vlan = "100",
        onuType = "F6600RV9.0.21",
        zone = "Zone 1",
        name = "lab",
        onuMode = "Routing",
        customProfile = "Generic_1",
    )
    private val sshMove = MoveCliRequest(
        fromBoard = 1,
        fromPort = 6,
        fromOntId = 16,
        toBoard = 1,
        toPort = 7,
        toOntId = 16,
        sn = "ZTEGDC47BFFD",
        lineProfileId = 10,
        serviceProfileId = 10,
        description = "lab",
        vlan = 100,
    )
    private val cloudMove = SmartOltMoveCommand(oltId = "gigafiber-ma5608t", board = "1", port = "7")
    private val sshDelete = DeleteCliRequest(board = 1, port = 6, ontId = 16)
    private val sshReboot = RebootCliRequest(board = 1, port = 6, ontId = 16)

    @Test
    fun `authorize SMARTOLT calls cloud not ssh`() {
        props.authorize = OnuWriteProvider.SMARTOLT
        every { smartOlt.authorize(cloudAuthorize) } returns SmartOltWriteResult(true, "cloud_1_6_16")

        val result = router().authorize(sshAuthorize, cloudAuthorize)

        assertEquals("cloud_1_6_16", result.uniqueExternalId)
        verify(exactly = 1) { smartOlt.authorize(cloudAuthorize) }
        verify(exactly = 0) { commandService.authorize(any()) }
    }

    @Test
    fun `authorize GATEWAY calls ssh not cloud`() {
        props.authorize = OnuWriteProvider.GATEWAY
        every { commandService.authorize(sshAuthorize) } returns AuthorizeCliResult(16, emptyList())

        router().authorize(sshAuthorize, cloudAuthorize)

        verify(exactly = 1) { commandService.authorize(sshAuthorize) }
        verify(exactly = 0) { smartOlt.authorize(any()) }
    }

    @Test
    fun `delete SMARTOLT calls cloud not ssh`() {
        props.delete = OnuWriteProvider.SMARTOLT
        every { smartOlt.delete("ext-1") } returns SmartOltWriteResult(true, "ext-1")

        router().delete(sshDelete, "ext-1")

        verify(exactly = 1) { smartOlt.delete("ext-1") }
        verify(exactly = 0) { commandService.delete(any()) }
    }

    @Test
    fun `delete GATEWAY calls ssh not cloud`() {
        props.delete = OnuWriteProvider.GATEWAY

        router().delete(sshDelete, "ext-1")

        verify(exactly = 1) { commandService.delete(sshDelete) }
        verify(exactly = 0) { smartOlt.delete(any()) }
    }

    @Test
    fun `reboot SMARTOLT calls cloud not ssh`() {
        props.reboot = OnuWriteProvider.SMARTOLT
        every { smartOlt.reboot("ext-1") } returns SmartOltWriteResult(true, "ext-1")

        router().reboot(sshReboot, "ext-1")

        verify(exactly = 1) { smartOlt.reboot("ext-1") }
        verify(exactly = 0) { commandService.reboot(any()) }
    }

    @Test
    fun `reboot GATEWAY calls ssh not cloud`() {
        props.reboot = OnuWriteProvider.GATEWAY

        router().reboot(sshReboot, "ext-1")

        verify(exactly = 1) { commandService.reboot(sshReboot) }
        verify(exactly = 0) { smartOlt.reboot(any()) }
    }

    @Test
    fun `move SMARTOLT calls cloud not ssh`() {
        props.move = OnuWriteProvider.SMARTOLT
        every { smartOlt.move("ZTEGDC47BFFD", cloudMove) } returns SmartOltWriteResult(true, "ext-1")

        router().move(sshMove, "ZTEGDC47BFFD", cloudMove)

        verify(exactly = 1) { smartOlt.move("ZTEGDC47BFFD", cloudMove) }
        verify(exactly = 0) { commandService.move(any()) }
    }

    @Test
    fun `move GATEWAY calls ssh not cloud`() {
        props.move = OnuWriteProvider.GATEWAY

        router().move(sshMove, "ZTEGDC47BFFD", cloudMove)

        verify(exactly = 1) { commandService.move(sshMove) }
        verify(exactly = 0) { smartOlt.move(any(), any()) }
    }
}
