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
                "Success\nMA5608T#"
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

        assertTrue(commands.any { it == "interface gpon 0/0" })
        assertTrue(commands.any { it == "ont delete 2 5" })
        assertTrue(commands.any { it == "interface gpon 0/1" })
        assertTrue(commands.any { it.contains("ont add 0 5 sn-auth 4857544311E70E9A") })
    }

    @Test
    fun `delete emite ont delete`() {
        service.delete(DeleteCliRequest(board = 1, port = 0, ontId = 5))

        assertTrue(commands.any { it == "interface gpon 0/1" })
        assertTrue(commands.any { it == "ont delete 0 5" })
        assertTrue(commands.any { it == "quit" })
    }

    @Test
    fun `reboot emite ont reboot`() {
        service.reboot(RebootCliRequest(board = 1, port = 0, ontId = 5))

        assertTrue(commands.any { it == "interface gpon 0/1" })
        assertTrue(commands.any { it == "ont reboot 0 5" })
        assertTrue(commands.any { it == "quit" })
    }

    @Test
    fun `updateWan recrea el service-port cuando cambia la VLAN`() {
        val executed = service.updateWan(UpdateWanCliRequest(board = 1, port = 0, ontId = 5, vlan = 120))

        assertTrue(commands.any { it == "undo service-port port 0/1/0 ont 5" })
        assertTrue(
            commands.any {
                it == "service-port vlan 120 gpon 0/1/0 ont 5 gemport 1 multi-service " +
                    "user-vlan 120 tag-transform translate"
            }
        )
        assertTrue(commands.none { it.startsWith("ont ipconfig") })
        assertEquals(commands, executed)
    }

    @Test
    fun `updateWan emite ont ipconfig estatico con gateway y dns`() {
        service.updateWan(
            UpdateWanCliRequest(
                board = 1,
                port = 0,
                ontId = 5,
                vlan = 120,
                ipAddress = "192.168.30.50",
                subnetMask = "255.255.255.0",
                gateway = "192.168.30.1",
                dns1 = "8.8.8.8",
                dns2 = "8.8.4.4"
            )
        )

        assertTrue(commands.any { it == "interface gpon 0/1" })
        assertTrue(
            commands.any {
                it == "ont ipconfig 0 5 static ip-address 192.168.30.50 mask 255.255.255.0 " +
                    "gateway 192.168.30.1 pri-dns 8.8.8.8 slave-dns 8.8.4.4 vlan 120"
            }
        )
        assertTrue(commands.any { it == "quit" })
    }

    @Test
    fun `updateWan sin datos no emite comandos`() {
        val executed = service.updateWan(UpdateWanCliRequest(board = 1, port = 0, ontId = 5))

        assertTrue(executed.isEmpty())
        assertTrue(commands.isEmpty())
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
