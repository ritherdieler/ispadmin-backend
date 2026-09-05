package com.dscorp.wispadmin.oltgateway.service

import com.dscorp.wispadmin.oltgateway.config.OltGatewayProperties
import com.dscorp.wispadmin.oltgateway.ssh.OltCliBus
import com.dscorp.wispadmin.oltgateway.ssh.OltCommandExecutor
import com.dscorp.wispadmin.oltgateway.ssh.OltSshClient
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

/**
 * Live smoke: authorize + delete via [OltGatewayCommandService] (same CLI stack as Gateway HTTP).
 *
 *   OLT_WRITE_LIVE=true
 *   OLT_GATEWAY_PASSWORD=... (optional)
 *
 * Uses a disposable probe SN on board/port 1 (not customer port 6).
 */
@Tag("live")
class OltGatewayDeleteLiveSmokeTest {

    @Test
    fun `delete via gateway command service removes ONT from OLT`() {
        assumeTrue(System.getenv("OLT_WRITE_LIVE") == "true") { "set OLT_WRITE_LIVE=true" }

        val board = (System.getenv("OLT_WRITE_BOARD") ?: "1").toInt()
        val port = (System.getenv("OLT_WRITE_PORT") ?: "1").toInt()
        val ontId = (System.getenv("OLT_WRITE_ONT_ID") ?: "94").toInt()
        val sn = System.getenv("OLT_WRITE_SN") ?: "ZTEGDC47BFFE"

        val props = OltGatewayProperties().apply {
            host = System.getenv("OLT_GATEWAY_HOST") ?: "10.11.104.2"
            this.port = (System.getenv("OLT_GATEWAY_PORT") ?: "22").toInt()
            username = System.getenv("OLT_GATEWAY_USERNAME") ?: "oltadmin"
            password = System.getenv("OLT_GATEWAY_PASSWORD") ?: "GigaOlt2026"
            ssh.legacyAlgorithms = true
            commandTimeoutMs = 120_000
            writes.enabled = true
            writes.defaultLineProfileId = 10
            writes.defaultServiceProfileId = 10
            session.keepaliveEnabled = false
            session.poolSize = 1
        }

        val ssh = OltSshClient(props)
        val bus = OltCliBus(ssh, props).also { it.start() }
        val executor = OltCommandExecutor(bus)
        val commands = OltGatewayCommandService(
            runCommand = executor::run,
            properties = props,
            inWriteJob = { block -> executor.write { block() } },
        )

        fun bySn(): String = executor.run("display ont info by-sn $sn")
        fun ontPresent(output: String): Boolean =
            output.contains(Regex("""(?i)F/S/P\s*:\s*0/$board/$port""")) ||
                output.contains(Regex("""(?i)ONT-ID\s*:\s*$ontId\b"""))

        try {
            runCatching {
                commands.delete(DeleteCliRequest(board = board, port = port, ontId = ontId))
            }

            commands.authorize(
                AuthorizeCliRequest(
                    board = board,
                    port = port,
                    ontId = ontId,
                    sn = sn,
                    lineProfileId = props.writes.defaultLineProfileId,
                    serviceProfileId = props.writes.defaultServiceProfileId,
                    description = "gateway-delete-smoke",
                    vlan = 100,
                )
            )

            val afterAdd = bySn()
            assertTrue(ontPresent(afterAdd), "ONT should exist after authorize: ${afterAdd.takeLast(500)}")

            commands.delete(DeleteCliRequest(board = board, port = port, ontId = ontId))

            val afterDelete = bySn()
            assertTrue(
                afterDelete.contains("The required ONT does not exist", ignoreCase = true),
                "ONT should be gone after gateway delete: ${afterDelete.takeLast(500)}",
            )
            assertFalse(ontPresent(afterDelete), "ONT markers still present: ${afterDelete.takeLast(500)}")

            println("LIVE GATEWAY DELETE OK sn=$sn fsp=0/$board/$port ont=$ontId")
        } finally {
            runCatching {
                commands.delete(DeleteCliRequest(board = board, port = port, ontId = ontId))
            }
            bus.close()
            ssh.close()
        }
    }
}
