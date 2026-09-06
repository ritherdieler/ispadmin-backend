package com.dscorp.wispadmin.oltgateway.service

import com.dscorp.wispadmin.oltgateway.config.OltGatewayProperties
import com.dscorp.wispadmin.oltgateway.exception.OltWritesDisabledException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class OltGatewayCommandServiceTest {

    private val commands = mutableListOf<String>()
    private lateinit var properties: OltGatewayProperties
    private lateinit var service: OltGatewayCommandService

    @BeforeEach
    fun setUp() {
        commands.clear()
        properties = OltGatewayProperties().apply {
            writes.enabled = true
        }
        service = OltGatewayCommandService(
            runCommand = { cmd ->
                commands.add(cmd)
                if (cmd.startsWith("ont delete")) {
                    "  Number of ONTs that can be deleted: 1, success: 1\nMA5608T#"
                } else {
                    "Success\nMA5608T#"
                }
            },
            properties = properties
        )
    }

    @Test
    fun `authorize emite secuencia CLI ont add y service-port`() {
        val result = service.authorize(
            AuthorizeCliRequest(
                board = 0,
                port = 2,
                ontId = 5,
                sn = "4857544311E70E9A",
                lineProfileId = 10,
                serviceProfileId = 10,
                description = "cliente_demo",
                vlan = 100
            )
        )

        assertEquals(5, result.ontId)
        assertTrue(commands.any { it == "interface gpon 0/0" })
        assertTrue(commands.any { it.contains("ont add 2 5 sn-auth 4857544311E70E9A") })
        assertTrue(commands.any { it.contains("ont-lineprofile-id 10") })
        assertTrue(commands.any { it.contains("ont-srvprofile-id 10") })
        assertTrue(commands.any { it.startsWith("service-port vlan 100 gpon 0/0/2 ont 5") })
        assertTrue(commands.any { it == "quit" })
    }

    @Test
    fun `move emite ont delete y ont add en nuevo puerto`() {
        service.move(
            MoveCliRequest(
                fromBoard = 0,
                fromPort = 2,
                fromOntId = 5,
                toBoard = 1,
                toPort = 0,
                toOntId = 5,
                sn = "4857544311E70E9A",
                lineProfileId = 10,
                serviceProfileId = 10,
                description = "cliente_demo",
                vlan = 100
            )
        )

        assertTrue(commands.first() == "undo service-port port 0/0/2 ont 5")
        assertTrue(commands.any { it == "interface gpon 0/0" })
        assertTrue(commands.any { it == "ont delete 2 5" })
        assertTrue(commands.any { it == "interface gpon 0/1" })
        assertTrue(commands.any { it.contains("ont add 0 5 sn-auth 4857544311E70E9A") })
    }

    @Test
    fun `delete emite undo service-port antes de ont delete`() {
        service.delete(DeleteCliRequest(board = 1, port = 0, ontId = 5))

        assertEquals(
            listOf(
                "undo service-port port 0/1/0 ont 5",
                "interface gpon 0/1",
                "ont delete 0 5",
                "quit",
            ),
            commands,
        )
    }

    @Test
    fun `delete es idempotente si ONT ya no existe en OLT`() {
        val alreadyGone = OltGatewayCommandService(
            runCommand = { cmd ->
                commands.add(cmd)
                if (cmd.startsWith("ont delete")) {
                    "  Failure: The ONT does not exist\nMA5608T#"
                } else {
                    "Success\nMA5608T#"
                }
            },
            properties = properties,
        )

        alreadyGone.delete(DeleteCliRequest(board = 1, port = 0, ontId = 5))

        assertTrue(commands.any { it == "ont delete 0 5" })
    }

    @Test
    fun `delete lanza si ont delete falla por otra causa`() {
        val failing = OltGatewayCommandService(
            runCommand = { cmd ->
                commands.add(cmd)
                if (cmd.startsWith("ont delete")) {
                    "  Failure: The ONT is online\nMA5608T#"
                } else {
                    "Success\nMA5608T#"
                }
            },
            properties = properties,
        )

        assertThrows(IllegalStateException::class.java) {
            failing.delete(DeleteCliRequest(board = 1, port = 0, ontId = 5))
        }
    }

    @Test
    fun `reboot emite ont reboot`() {
        service.reboot(RebootCliRequest(board = 1, port = 0, ontId = 5))

        assertTrue(commands.any { it == "interface gpon 0/1" })
        assertTrue(commands.any { it == "ont reboot 0 5" })
        assertTrue(commands.any { it == "quit" })
    }

    @Test
    fun `planAuthorize devuelve la misma secuencia que ejecutaria authorize`() {
        val request = AuthorizeCliRequest(
            board = 0,
            port = 2,
            ontId = 5,
            sn = "4857544311E70E9A",
            lineProfileId = 10,
            serviceProfileId = 10,
            description = "cliente_demo",
            vlan = 100
        )

        val planned = service.planAuthorize(request)
        val executed = service.authorize(request).commands

        assertEquals(executed, planned)
    }

    @Test
    fun `planAuthorize no toca la OLT ni exige escrituras habilitadas`() {
        properties.writes.enabled = false

        val planned = service.planAuthorize(
            AuthorizeCliRequest(
                board = 0,
                port = 2,
                ontId = 7,
                sn = "4857544311E70E9A",
                lineProfileId = 10,
                serviceProfileId = 10,
                description = "cliente_demo",
                vlan = 100
            )
        )

        assertTrue(commands.isEmpty())
        assertTrue(planned.any { it.contains("ont add 2 7 sn-auth 4857544311E70E9A") })
    }

    @Test
    fun `writes disabled lanza excepcion`() {
        properties.writes.enabled = false

        assertThrows(OltWritesDisabledException::class.java) {
            service.reboot(RebootCliRequest(board = 0, port = 0, ontId = 1))
        }
        assertTrue(commands.isEmpty())
    }
}
