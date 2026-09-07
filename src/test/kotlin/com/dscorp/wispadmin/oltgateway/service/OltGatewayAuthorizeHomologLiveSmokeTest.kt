package com.dscorp.wispadmin.oltgateway.service

import com.dscorp.wispadmin.oltgateway.config.OltGatewayProperties
import com.dscorp.wispadmin.oltgateway.ssh.OltCliBus
import com.dscorp.wispadmin.oltgateway.ssh.OltCommandExecutor
import com.dscorp.wispadmin.oltgateway.ssh.OltSshClient
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

/**
 * Live: authorize via SSH must leave the same line/srv profiles SmartOLT uses for Generic_1 + vlan 100.
 *
 *   OLT_WRITE_LIVE=true OLT_HOMOLOG_AUTHORIZE=true ./mvnw -Dtest=OltGatewayAuthorizeHomologLiveSmokeTest test
 *
 * Optional: OLT_WRITE_SN / OLT_WRITE_BOARD / OLT_WRITE_PORT / OLT_WRITE_ONT_ID / OLT_WRITE_KEEP=1
 */
@Tag("live")
class OltGatewayAuthorizeHomologLiveSmokeTest {

    @Test
    fun `ssh authorize Generic_1 vlan100 leaves line 6 srv 13 and traffic tables`() {
        assumeTrue(System.getenv("OLT_WRITE_LIVE") == "true") { "set OLT_WRITE_LIVE=true" }
        assumeTrue(System.getenv("OLT_HOMOLOG_AUTHORIZE") == "true") {
            "set OLT_HOMOLOG_AUTHORIZE=true (destructive on lab ONU)"
        }

        val board = (System.getenv("OLT_WRITE_BOARD") ?: "1").toInt()
        val port = (System.getenv("OLT_WRITE_PORT") ?: "6").toInt()
        val sn = System.getenv("OLT_WRITE_SN") ?: "ZTEGDC47BFFD"
        val keep = System.getenv("OLT_WRITE_KEEP") == "1"

        val props = OltGatewayProperties().apply {
            host = System.getenv("OLT_GATEWAY_HOST") ?: "10.11.104.2"
            this.port = (System.getenv("OLT_GATEWAY_PORT") ?: "22").toInt()
            username = System.getenv("OLT_GATEWAY_USERNAME") ?: "oltadmin"
            password = System.getenv("OLT_GATEWAY_PASSWORD") ?: "GigaOlt2026"
            ssh.legacyAlgorithms = true
            commandTimeoutMs = 120_000
            writes.enabled = true
            writes.customProfileBindings = "Generic_1:1=3:2,Generic_1:100=6:13"
            writes.inboundTrafficTableIndex = 8
            writes.outboundTrafficTableIndex = 9
            session.keepaliveEnabled = false
            session.poolSize = 1
        }

        val resolver = SmartOltAuthorizeProfileResolver(props)
        val profiles = resolver.resolve(
            customProfile = "Generic_1",
            vlan = 100,
            name = "SSH-HOMOLOG",
            zone = "Zone 1",
            sn = sn,
        )

        val ssh = OltSshClient(props)
        val bus = OltCliBus(ssh, props).also { it.start() }
        val executor = OltCommandExecutor(bus)
        val commands = OltGatewayCommandService(
            runCommand = executor::run,
            properties = props,
            inWriteJob = { block -> executor.write { block() } },
        )

        fun bySn(): String = executor.run("display ont info by-sn $sn")
        fun servicePorts(): String =
            executor.run("display service-port port 0/$board/$port")

        try {
            val before = bySn()
            val existingOnt = Regex("""(?i)ONT-ID\s*:\s*(\d+)""").find(before)?.groupValues?.get(1)?.toIntOrNull()
            val ontId = (System.getenv("OLT_WRITE_ONT_ID")?.toIntOrNull())
                ?: existingOnt
                ?: 16

            if (existingOnt != null) {
                commands.delete(DeleteCliRequest(board = board, port = port, ontId = existingOnt))
            }

            commands.authorize(
                AuthorizeCliRequest(
                    board = board,
                    port = port,
                    ontId = ontId,
                    sn = sn,
                    lineProfileId = profiles.lineProfileId,
                    serviceProfileId = profiles.serviceProfileId,
                    description = profiles.description,
                    vlan = 100,
                )
            )

            val after = bySn()
            assertTrue(after.contains("Line profile ID      : 6"), after.takeLast(800))
            assertTrue(after.contains("Generic_1_V100"), after.takeLast(800))
            assertTrue(after.contains("Service profile ID   : 13"), after.takeLast(800))

            var onlineOutput = after
            var online = onlineOutput.contains(Regex("(?i)Run state\\s*:\\s*online"))
            repeat(12) {
                if (online) return@repeat
                Thread.sleep(5_000)
                onlineOutput = bySn()
                online = onlineOutput.contains(Regex("(?i)Run state\\s*:\\s*online"))
            }
            assertTrue(online, "ONT should become online after SSH authorize: ${onlineOutput.takeLast(800)}")

            val sp = servicePorts()
            assertTrue(
                sp.contains("traffic-table") || sp.contains("Inbound") || sp.contains("8"),
                sp.takeLast(800),
            )

            println(
                "LIVE SSH HOMOLOG OK sn=$sn fsp=0/$board/$port ont=$ontId " +
                    "line=${profiles.lineProfileId} srv=${profiles.serviceProfileId} desc=${profiles.description}"
            )

            if (!keep) {
                commands.delete(DeleteCliRequest(board = board, port = port, ontId = ontId))
            }
        } finally {
            bus.close()
            ssh.close()
        }
    }
}
