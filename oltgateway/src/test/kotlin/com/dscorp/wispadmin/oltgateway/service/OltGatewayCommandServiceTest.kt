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
                cliOk(cmd)
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
        assertTrue(commands.any { it.contains("inbound traffic-table index 8") })
        assertTrue(commands.any { it.contains("outbound traffic-table index 9") })
        assertTrue(commands.any { it == "quit" })
    }

    @Test
    fun `authorize usa el job de authorize y delete el de write`() {
        var authorizeJobs = 0
        var writeJobs = 0
        val split = OltGatewayCommandService(
            runCommand = { cmd ->
                commands.add(cmd)
                cliOk(cmd)
            },
            properties = properties,
            inWriteJob = { block ->
                writeJobs += 1
                block()
            },
            inAuthorizeJob = { block ->
                authorizeJobs += 1
                block()
            },
        )
        split.authorize(
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
        split.delete(DeleteCliRequest(board = 0, port = 2, ontId = 5))
        assertEquals(1, authorizeJobs)
        assertEquals(1, writeJobs)
    }

    @Test
    fun `authorize con perfiles Generic_1_V100 emite line 6 srv 13 y traffic tables`() {
        val result = service.authorize(
            AuthorizeCliRequest(
                board = 1,
                port = 6,
                ontId = 16,
                sn = "ZTEGDC47BFFD",
                lineProfileId = 6,
                serviceProfileId = 13,
                description = "HOMOLOG-GW-TEST_zone_Zone 1_authd_20260902",
                vlan = 100
            )
        )

        assertEquals(16, result.ontId)
        assertTrue(commands.any { it.contains("ont-lineprofile-id 6") })
        assertTrue(commands.any { it.contains("ont-srvprofile-id 13") })
        assertTrue(
            commands.any {
                it == "service-port vlan 100 gpon 0/1/6 ont 16 gemport 1 multi-service user-vlan 100 " +
                    "tag-transform translate inbound traffic-table index 8 outbound traffic-table index 9"
            }
        )
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
    fun `authorize de ONU lab emite service-port VLAN 1000 en gem 2`() {
        service.authorize(
            AuthorizeCliRequest(
                board = 1,
                port = 6,
                ontId = 119,
                sn = "VSOL0031C0B6",
                lineProfileId = 12,
                serviceProfileId = 13,
                description = "lab_vsol",
                vlan = 100,
                mgmtVlan = 1000,
                mgmtGemport = 2,
            )
        )

        assertTrue(
            commands.any {
                it == "service-port vlan 100 gpon 0/1/6 ont 119 gemport 1 multi-service user-vlan 100 " +
                    "tag-transform translate inbound traffic-table index 8 outbound traffic-table index 9"
            }
        )
        assertTrue(
            commands.any {
                it == "service-port vlan 1000 gpon 0/1/6 ont 119 gemport 2 multi-service user-vlan 1000 " +
                    "tag-transform translate inbound traffic-table index 8 outbound traffic-table index 9"
            }
        )
        assertTrue(commands.any { it.contains("ont-lineprofile-id 12") })
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
    fun `authorize lanza si ont add choca con un ONT ya ocupado`() {
        val colliding = OltGatewayCommandService(
            runCommand = { cmd ->
                commands.add(cmd)
                if (cmd.startsWith("ont add")) {
                    "  Failure: The ONT ID already exists\nMA5608T#"
                } else {
                    "Success\nMA5608T#"
                }
            },
            properties = properties,
        )

        val ex = assertThrows(IllegalStateException::class.java) {
            colliding.authorize(
                AuthorizeCliRequest(
                    board = 1,
                    port = 6,
                    ontId = 1,
                    sn = "VSOL0031C0B6",
                    lineProfileId = 12,
                    serviceProfileId = 13,
                    description = "lab_vsol",
                    vlan = 100,
                )
            )
        }
        assertTrue(ex.message!!.contains("ONT ID already exists"))
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

    @Test
    fun `removeServicePort emite undo service-port vlan del ONT`() {
        service.removeServicePort(board = 0, port = 1, ontId = 5, vlan = 1)

        assertTrue(commands.any { it == "undo service-port vlan 1 gpon 0/0/1 ont 5" })
    }

    @Test
    fun `displayServicePorts pide la tabla del ONT`() {
        service.displayServicePorts(board = 0, port = 1, ontId = 5)
        assertTrue(commands.any { it == "display service-port port 0/0/1 ont 5" })
    }

    @Test
    fun `parseServicePortVlans extrae VLAN 1 y 100`() {
        val output = """
            service-port 12 vlan 1 gpon 0/0/1 ont 5 gemport 1 multi-service user-vlan 1
            service-port 18 vlan 100 gpon 0/0/1 ont 5 gemport 2 multi-service user-vlan 100
        """.trimIndent()
        assertEquals(setOf(1, 100), service.parseServicePortVlans(output))
    }

    @Test
    fun `ensureMgmtServicePort no escribe si VLAN 1000 ya esta`() {
        val existing = OltGatewayCommandService(
            runCommand = { cmd ->
                commands.add(cmd)
                if (cmd.startsWith("display service-port")) {
                    "service-port 18 vlan 100 gpon 0/1/6 ont 116 gemport 1\n" +
                        "service-port 1857 vlan 1000 gpon 0/1/6 ont 116 gemport 2\n"
                } else {
                    cliOk(cmd)
                }
            },
            properties = properties,
        )

        val vlans = existing.ensureMgmtServicePort(
            EnsureMgmtServicePortRequest(board = 1, port = 6, ontId = 116),
        )

        assertEquals(setOf(100, 1000), vlans)
        assertTrue(commands.none { it.startsWith("ont modify") })
        assertTrue(commands.none { it.startsWith("service-port vlan 1000") })
        assertTrue(commands.none { it.startsWith("gem mapping") })
    }

    @Test
    fun `ensureMgmtServicePort abre SP 1000 en gem del profile actual sin ont modify`() {
        var displays = 0
        val creating = OltGatewayCommandService(
            runCommand = { cmd ->
                commands.add(cmd)
                when {
                    cmd.startsWith("display service-port") -> {
                        displays += 1
                        if (displays == 1) {
                            "service-port 18 vlan 100 gpon 0/1/6 ont 10 gemport 1\n"
                        } else {
                            "service-port 18 vlan 100 gpon 0/1/6 ont 10 gemport 1\n" +
                                "service-port 99 vlan 1000 gpon 0/1/6 ont 10 gemport 2\n"
                        }
                    }
                    cmd.startsWith("display ont info") ->
                        "  F/S/P                   : 0/1/6\n" +
                            "  ONT-ID                  : 10\n" +
                            "  SN                      : 56534F4C0031C0B6 (VSOL-0031C0B6)\n" +
                            "  Line profile ID      : 12\n" +
                            "  Line profile name    : Generic_1_V100M1000MGM\n"
                    cmd.startsWith("display ont-lineprofile") ->
                        "   <Gem Index 1>\n    1       100   -        -\n" +
                            "   <Gem Index 2>\n    1       1000  -        -\n"
                    else -> cliOk(cmd)
                }
            },
            properties = properties,
        )

        val vlans = creating.ensureMgmtServicePort(
            EnsureMgmtServicePortRequest(board = 1, port = 6, ontId = 10, vlan = 1000),
        )

        assertEquals(setOf(100, 1000), vlans)
        assertTrue(commands.none { it.startsWith("ont modify") })
        assertTrue(commands.none { it.contains("ont-lineprofile-id 12") })
        assertTrue(
            commands.any {
                it == "service-port vlan 1000 gpon 0/1/6 ont 10 gemport 2 multi-service user-vlan 1000 " +
                    "tag-transform translate inbound traffic-table index 8 outbound traffic-table index 9"
            },
        )
    }

    @Test
    fun `ensureMgmtServicePort con VLAN 1 anade mapping 1000 al profile actual`() {
        var displays = 0
        val creating = OltGatewayCommandService(
            runCommand = { cmd ->
                commands.add(cmd)
                when {
                    cmd.startsWith("display service-port") -> {
                        displays += 1
                        if (displays == 1) {
                            "service-port 1191 vlan 1 gpon 0/1/2 ont 77 gemport 1\n" +
                                "service-port 666 vlan 100 gpon 0/1/2 ont 77 gemport 1\n"
                        } else {
                            "service-port 1191 vlan 1 gpon 0/1/2 ont 77 gemport 1\n" +
                                "service-port 666 vlan 100 gpon 0/1/2 ont 77 gemport 1\n" +
                                "service-port 99 vlan 1000 gpon 0/1/2 ont 77 gemport 1\n"
                        }
                    }
                    cmd.startsWith("display ont info") ->
                        "  F/S/P                   : 0/1/2\n" +
                            "  ONT-ID                  : 77\n" +
                            "  SN                      : 56534F4C0000E274 (VSOL-0000E274)\n" +
                            "  Line profile ID      : 5\n" +
                            "  Line profile name    : Generic_1_HF291F96D\n"
                    cmd.startsWith("display ont-lineprofile") ->
                        "   <Gem Index 1>\n    1       1     -        -\n" +
                            "    2       100   -        -\n"
                    else -> cliOk(cmd)
                }
            },
            properties = properties,
        )

        val vlans = creating.ensureMgmtServicePort(
            EnsureMgmtServicePortRequest(board = 1, port = 2, ontId = 77, vlan = 1000),
        )

        assertEquals(setOf(1, 100, 1000), vlans)
        assertTrue(commands.none { it.startsWith("ont modify") })
        assertTrue(commands.any { it == "ont-lineprofile gpon profile-id 5" })
        assertTrue(commands.any { it == "gem mapping 1 3 vlan 1000" })
        assertTrue(commands.any { it == "commit" })
        assertTrue(
            commands.any {
                it.startsWith("service-port vlan 1000 gpon 0/1/2 ont 77 gemport 1 ")
            },
        )
    }

    @Test
    fun `ensureMgmtServicePort con internet VLAN 100 anade mapping 1000 al mismo gem`() {
        var displays = 0
        val creating = OltGatewayCommandService(
            runCommand = { cmd ->
                commands.add(cmd)
                when {
                    cmd.startsWith("display service-port") -> {
                        displays += 1
                        if (displays == 1) {
                            "service-port 18 vlan 100 gpon 0/1/6 ont 20 gemport 1\n"
                        } else {
                            "service-port 18 vlan 100 gpon 0/1/6 ont 20 gemport 1\n" +
                                "service-port 99 vlan 1000 gpon 0/1/6 ont 20 gemport 1\n"
                        }
                    }
                    cmd.startsWith("display ont info") ->
                        "  F/S/P                   : 0/1/6\n" +
                            "  ONT-ID                  : 20\n" +
                            "  SN                      : 56534F4C0086ACF9 (VSOL-0086ACF9)\n" +
                            "  Line profile ID      : 6\n" +
                            "  Line profile name    : Generic_1_V100\n"
                    cmd.startsWith("display ont-lineprofile") ->
                        "   <Gem Index 1>\n    1       100   -        -\n"
                    else -> cliOk(cmd)
                }
            },
            properties = properties,
        )

        val vlans = creating.ensureMgmtServicePort(
            EnsureMgmtServicePortRequest(board = 1, port = 6, ontId = 20, vlan = 1000),
        )

        assertEquals(setOf(100, 1000), vlans)
        assertTrue(commands.none { it.startsWith("ont modify") })
        assertTrue(commands.any { it == "gem mapping 1 2 vlan 1000" })
        assertTrue(commands.any { it.startsWith("service-port vlan 1000 gpon 0/1/6 ont 20 gemport 1 ") })
    }

    @Test
    fun `ensureMgmtServicePort trata already exists como exito si el display muestra 1000`() {
        var displays = 0
        val colliding = OltGatewayCommandService(
            runCommand = { cmd ->
                commands.add(cmd)
                when {
                    cmd.startsWith("display service-port") -> {
                        displays += 1
                        if (displays == 1) {
                            "service-port 18 vlan 100 gpon 0/1/6 ont 10 gemport 1\n"
                        } else {
                            "service-port 18 vlan 100 gpon 0/1/6 ont 10 gemport 1\n" +
                                "service-port 99 vlan 1000 gpon 0/1/6 ont 10 gemport 2\n"
                        }
                    }
                    cmd.startsWith("display ont info") ->
                        "  F/S/P                   : 0/1/6\n" +
                            "  ONT-ID                  : 10\n" +
                            "  SN                      : 56534F4C0031C0B6 (VSOL-0031C0B6)\n" +
                            "  Line profile ID      : 12\n" +
                            "  Line profile name    : Generic_1_V100M1000MGM\n"
                    cmd.startsWith("display ont-lineprofile") ->
                        "   <Gem Index 1>\n    1       100   -        -\n" +
                            "   <Gem Index 2>\n    1       1000  -        -\n"
                    cmd.startsWith("service-port vlan 1000") ->
                        "  Failure: The service virtual port already exists\nMA5608T#"
                    else -> cliOk(cmd)
                }
            },
            properties = properties,
        )

        val vlans = colliding.ensureMgmtServicePort(
            EnsureMgmtServicePortRequest(board = 1, port = 6, ontId = 10),
        )
        assertEquals(setOf(100, 1000), vlans)
        assertTrue(commands.none { it.startsWith("ont modify") })
    }

    private fun cliOk(cmd: String): String = when {
        cmd.startsWith("ont delete") ->
            "  Number of ONTs that can be deleted: 1, success: 1\nMA5608T#"
        cmd.startsWith("ont add") ->
            "  Number of ONTs that can be added: 1, success: 1\nMA5608T#"
        else -> "Success\nMA5608T#"
    }
}
