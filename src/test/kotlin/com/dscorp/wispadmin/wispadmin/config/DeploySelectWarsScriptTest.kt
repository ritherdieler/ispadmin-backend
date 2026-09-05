package com.dscorp.wispadmin.wispadmin.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Path

class DeploySelectWarsScriptTest {

    @Test
    fun onlyFlagSelectsExactWars() {
        val out = runSelect("--only", "oltgateway")
        assertEquals(0, out.exit)
        assertEquals("oltgateway", out.stdout.trim())
    }

    @Test
    fun onlyAcsSelectsAcs() {
        val out = runSelect("--only", "acs")
        assertEquals(0, out.exit)
        assertEquals("acs", out.stdout.trim())
    }

    @Test
    fun oltgatewayPathMapsToGatewayOnly() {
        val out = runSelect("--files-from", "-", stdin = "src/main/kotlin/com/dscorp/wispadmin/oltgateway/service/OnuActivationService.kt\n")
        assertEquals(0, out.exit)
        assertEquals("oltgateway", out.stdout.trim())
    }

    @Test
    fun eventsOrPomMapsToAllFour() {
        val out = runSelect("--files-from", "-", stdin = "src/main/kotlin/com/dscorp/wispadmin/events/PlatformEvent.kt\n")
        assertEquals(0, out.exit)
        assertEquals("acs,core,oltgateway,traffic", out.stdout.trim())
        val pom = runSelect("--files-from", "-", stdin = "pom.xml\n")
        assertEquals(0, pom.exit)
        assertEquals("acs,core,oltgateway,traffic", pom.stdout.trim())
    }

    @Test
    fun docsOnlyFailsAskingForOnly() {
        val out = runSelect("--files-from", "-", stdin = ".agent-docs/deploy-flow.md\n")
        assertTrue(out.exit != 0)
        assertTrue(out.combined.contains("--only"), out.combined)
    }

    @Test
    fun onlyFlagWinsOverMappedFiles() {
        val out = runSelect(
            "--only",
            "acs",
            "--files-from",
            "-",
            stdin = "src/main/kotlin/com/dscorp/wispadmin/oltgateway/service/OnuActivationService.kt\n",
        )
        assertEquals(0, out.exit)
        assertEquals("acs", out.stdout.trim())
    }

    private data class Proc(val exit: Int, val stdout: String, val combined: String)

    private fun runSelect(vararg args: String, stdin: String = ""): Proc {
        val root = Path.of(System.getProperty("user.dir"))
        val script = root.resolve("scripts/deploy-select-wars.sh").toFile()
        val pb = ProcessBuilder("bash", script.absolutePath, *args)
            .directory(root.toFile())
            .redirectErrorStream(true)
        val process = pb.start()
        process.outputStream.bufferedWriter().use { it.write(stdin) }
        val output = process.inputStream.bufferedReader().readText()
        return Proc(process.waitFor(), output, output)
    }
}
