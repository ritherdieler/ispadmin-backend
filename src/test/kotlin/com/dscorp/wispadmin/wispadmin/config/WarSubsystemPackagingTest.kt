package com.dscorp.wispadmin.wispadmin.config

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class WarSubsystemPackagingTest {

    @Test
    fun pomDeclaresSubsystemExcludesPlaceholder() {
        val root = Path.of(System.getProperty("user.dir"))
        val pom = Files.readString(root.resolve("pom.xml"))
        assertTrue(pom.contains("<subsystem.excludes>"), pom.take(400))
        assertTrue(pom.contains("\${subsystem.excludes}"), pom)
        assertTrue(pom.contains("subsystem.with"), pom)
        assertTrue(pom.contains("scripts/subsystems.sh"), pom)
    }

    @Test
    fun subsystemsScriptMapsKeysToPackagePaths() {
        val root = Path.of(System.getProperty("user.dir"))
        val script = Files.readString(root.resolve("scripts/subsystems.sh"))
        assertTrue(script.contains("observability"))
        assertTrue(script.contains("servicehealth"))
        assertTrue(script.contains("WEB-INF/classes/com/dscorp/wispadmin"))
        assertTrue(script.contains("--with"))
    }

    @Test
    fun subsystemsScriptAcceptsAnyCombination() {
        val root = Path.of(System.getProperty("user.dir"))
        val script = root.resolve("scripts/subsystems.sh").toFile()
        val process = ProcessBuilder("bash", script.absolutePath, "--with", "oltgateway,netdiag")
            .directory(root.toFile())
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText()
        assertTrue(process.waitFor() == 0, output)
        assertTrue(output.contains("SUBSYSTEM_DISABLED_KEYS="), output)
        assertTrue(output.contains("observability"), output)
        assertTrue(output.contains("traffic"), output)
        assertTrue(output.contains("servicehealth"), output)
        assertTrue(!output.contains("WEB-INF/classes/com/dscorp/wispadmin/oltgateway/**") || output.contains("SUBSYSTEM_EXCLUDES="), output)
        assertTrue(output.contains("WEB-INF/classes/com/dscorp/wispadmin/traffic/**"), output)
        assertTrue(!Regex("SUBSYSTEM_EXCLUDES=.*oltgateway").containsMatchIn(output), output)
    }

    @Test
    fun subsystemsScriptBakesAcsLabOverlayWhenServicehealthKept() {
        val root = Path.of(System.getProperty("user.dir"))
        val writeDir = Files.createTempDirectory("subsystem-acs")
        val script = root.resolve("scripts/subsystems.sh").toFile()
        val process = ProcessBuilder("bash", script.absolutePath, "--with", "servicehealth", "--write-dir", writeDir.toString())
            .directory(root.toFile())
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText()
        assertTrue(process.waitFor() == 0, output)
        val baked = Files.readString(writeDir.resolve("subsystem-enabled.properties"))
        assertTrue(baked.contains("gigafiber.subsystems.servicehealth.enabled=true"), baked)
        assertTrue(baked.contains("gigafiber.scheduling.enabled=true"), baked)
        assertTrue(baked.contains("service.health.enabled=true"), baked)
        assertTrue(baked.contains("service.health.acs-enabled=true"), baked)
        assertTrue(baked.contains("service.health.actions-enabled=true"), baked)
    }

    @Test
    fun subsystemsScriptBakesOltOpticalNetdiagWhenThoseKept() {
        val root = Path.of(System.getProperty("user.dir"))
        val writeDir = Files.createTempDirectory("subsystem-olt")
        val script = root.resolve("scripts/subsystems.sh").toFile()
        val process = ProcessBuilder(
            "bash",
            script.absolutePath,
            "--with",
            "servicehealth,oltgateway,netdiag,traffic",
            "--write-dir",
            writeDir.toString(),
        )
            .directory(root.toFile())
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText()
        assertTrue(process.waitFor() == 0, output)
        val baked = Files.readString(writeDir.resolve("subsystem-enabled.properties"))
        assertTrue(baked.contains("gigafiber.subsystems.oltgateway.enabled=true"), baked)
        assertTrue(baked.contains("gigafiber.subsystems.netdiag.enabled=true"), baked)
        assertTrue(baked.contains("gigafiber.subsystems.traffic.enabled=true"), baked)
        assertTrue(baked.contains("service.health.optical-enabled=true"), baked)
        assertTrue(baked.contains("olt.gateway.enabled=false"), baked)
        assertTrue(!baked.contains("olt.gateway.writes.enabled=true"), baked)
        assertTrue(!baked.contains("olt.gateway.sync.inventory-enabled=true"), baked)
        assertTrue(!baked.contains("olt.gateway.sync.lab-optical-ssh-enabled=true"), baked)
        assertTrue(baked.contains("net.diag.enabled=true"), baked)
        assertTrue(baked.contains("net.diag.snmp.trap.udp-enabled=false"), baked)
        assertTrue(baked.contains("net.diag.syslog.udp-enabled=false"), baked)
    }

    @Test
    fun verifyWarScriptGuardsEmptyWith() {
        val root = Path.of(System.getProperty("user.dir"))
        val script = Files.readString(root.resolve("scripts/verify-war.sh"))
        assertTrue(script.contains("[[ -n \"\$WITH\" ]]") || script.contains("[[ -n \"\${WITH") , script)
    }
}
