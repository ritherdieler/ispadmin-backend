package com.dscorp.wispadmin.wispadmin.config

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipFile

class WarSubsystemPackagingTest {

    @Test
    fun pomDeclaresSubsystemExcludesPlaceholder() {
        val root = Path.of(System.getProperty("user.dir"))
        val pom = Files.readString(root.resolve("pom.xml"))
        assertTrue(pom.contains("<subsystem.excludes>"), pom.take(400))
        assertTrue(pom.contains("\${subsystem.excludes}"), pom)
        assertTrue(pom.contains("subsystem.with"), pom)
        assertTrue(pom.contains("scripts/subsystems.sh"), pom)
        assertTrue(pom.contains("<id>traffic-war</id>"), pom)
        assertTrue(pom.contains("<id>traffic-staging-war</id>"), pom)
        assertTrue(pom.contains("ispadmin-staging-traffic"), pom)
        assertTrue(pom.contains("com.dscorp.wispadmin.traffic.TrafficApplication"), pom)
        assertTrue(pom.contains("<id>oltgateway-war</id>"), pom)
        assertTrue(pom.contains("<id>oltgateway-staging-war</id>"), pom)
        assertTrue(pom.contains("ispadmin-staging-oltgateway"), pom)
        assertTrue(pom.contains("com.dscorp.wispadmin.oltgateway.OltGatewayApplication"), pom)
        assertTrue(pom.contains("<id>acs-war</id>"), pom)
        assertTrue(pom.contains("<id>acs-staging-war</id>"), pom)
        assertTrue(pom.contains("ispadmin-staging-acs"), pom)
        assertTrue(pom.contains("com.dscorp.wispadmin.acs.AcsApplication"), pom)
    }

    @Test
    fun subsystemsScriptMapsKeysToPackagePaths() {
        val root = Path.of(System.getProperty("user.dir"))
        val script = Files.readString(root.resolve("scripts/subsystems.sh"))
        assertTrue(script.contains("observability"))
        assertTrue(script.contains("servicehealth"))
        assertTrue(script.contains("traffic"))
        assertTrue(script.contains("oltgateway"))
        assertTrue(script.contains("acs"))
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
        assertTrue(output.contains("WEB-INF/classes/com/dscorp/wispadmin/oltgateway/**"), output)
        assertTrue(output.contains("WEB-INF/classes/com/dscorp/wispadmin/acs/**"), output)
        assertTrue(Regex("SUBSYSTEM_EXCLUDES=.*oltgateway").containsMatchIn(output), output)
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
        assertTrue(baked.contains("traffic.client-enabled=true"), baked)
        assertTrue(baked.contains("olt.gateway.client-enabled=true"), baked)
        assertTrue(baked.contains("olt.gateway.acs.enabled=true"), baked)
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
        assertTrue(script.contains("acs"), script)
    }

    @Test
    fun pomExcludesFaceModelsFromEveryWar() {
        val pom = Files.readString(root().resolve("pom.xml"))
        val warPlugin = pom.substringAfter("maven-war-plugin").substringBefore("maven-jar-plugin")
        assertTrue(warPlugin.contains("WEB-INF/classes/models/**"), warPlugin)
        assertTrue(warPlugin.contains("WEB-INF/lib/tomcat-embed-*.jar"), warPlugin)
        assertTrue(warPlugin.contains("\${lib.excludes}"), warPlugin)
        assertTrue(pom.contains("<lib.excludes>"), pom.take(800))
        assertTrue(pom.contains("<lib.excludes.satellite>"), pom)
    }

    @Test
    fun satelliteProfilesExcludeUnusedJarsAndGatewayKeepsSshSnmpSwagger() {
        val pom = Files.readString(root().resolve("pom.xml"))
        val satelliteLibs = listOf(
            "WEB-INF/lib/firebase-admin-*.jar",
            "WEB-INF/lib/poi-*.jar",
            "WEB-INF/lib/meilisearch-*.jar",
            "WEB-INF/lib/jts-*.jar",
            "WEB-INF/lib/hibernate-spatial-*.jar",
            "WEB-INF/lib/spring-webflux-*.jar",
        )
        satelliteLibs.forEach { jar ->
            assertTrue(pom.contains(jar), "satellite lib.excludes must list $jar")
        }
        listOf("acs-war", "acs-staging-war", "traffic-war", "traffic-staging-war").forEach { profileId ->
            val slice = profileProperties(pom, profileId)
            assertTrue(slice.contains("\${lib.excludes.satellite}"), slice)
            assertTrue(slice.contains("WEB-INF/lib/sshd-*.jar"), "$profileId must drop sshd: $slice")
            assertTrue(slice.contains("WEB-INF/lib/snmp4j-*.jar"), "$profileId must drop snmp4j: $slice")
            assertTrue(slice.contains("WEB-INF/lib/swagger-ui-*.jar"), "$profileId must drop swagger-ui: $slice")
        }
        listOf("oltgateway-war", "oltgateway-staging-war").forEach { profileId ->
            val slice = profileProperties(pom, profileId)
            assertTrue(slice.contains("\${lib.excludes.satellite}"), slice)
            assertFalse(slice.contains("sshd-*.jar"), "$profileId must keep sshd: $slice")
            assertFalse(slice.contains("snmp4j-*.jar"), "$profileId must keep snmp4j: $slice")
            assertFalse(slice.contains("swagger-ui-*.jar"), "$profileId must keep swagger-ui: $slice")
        }
    }

    @Test
    fun satelliteProfilesExcludeCoreFlywayMigrations() {
        val pom = Files.readString(root().resolve("pom.xml"))
        listOf(
            "traffic-war",
            "traffic-staging-war",
            "oltgateway-war",
            "oltgateway-staging-war",
            "acs-war",
            "acs-staging-war",
        ).forEach { profileId ->
            val slice = profileProperties(pom, profileId)
            assertTrue(
                slice.contains("WEB-INF/classes/db/migration/**"),
                "$profileId must omit core Flyway scripts: $slice",
            )
        }
        val traffic = Files.readString(root().resolve("src/main/resources/application-traffic.properties"))
        val acs = Files.readString(root().resolve("src/main/resources/application-acs.properties"))
        val gateway = Files.readString(root().resolve("src/main/resources/application-oltgateway.properties"))
        assertTrue(Regex("""^spring\.flyway\.enabled=false\s*$""", RegexOption.MULTILINE).containsMatchIn(traffic), traffic)
        assertTrue(Regex("""^spring\.flyway\.enabled=true\s*$""", RegexOption.MULTILINE).containsMatchIn(acs), acs)
        assertTrue(acs.contains("classpath:db/acs"), acs)
        assertTrue(gateway.contains("classpath:db/oltgateway"), gateway)
    }

    @Test
    fun deployScriptPromotesSatelliteWarsOutOfIsolatedTargetDirectory() {
        val script = Files.readString(root().resolve("scripts/deploy.sh"))
        assertTrue(script.contains("promote_packaged_war"), script.take(400))
        assertTrue(script.contains("target/\${name%.war}/\$name"), script)
    }

    @Test
    fun packagedWarsOmitFaceModelFilesWhenPresent() {
        val target = root().resolve("target")
        val wars = listOf(
            "ispadmin.war",
            "ispadmin-staging.war",
            "ispadmin-staging-acs.war",
            "ispadmin-staging-traffic.war",
            "ispadmin-staging-oltgateway.war",
        ).map { name ->
            val flat = target.resolve(name)
            val nested = target.resolve(name.removeSuffix(".war")).resolve(name)
            when {
                Files.isRegularFile(flat) -> flat
                Files.isRegularFile(nested) -> nested
                else -> null
            }
        }.filterNotNull()
        if (wars.isEmpty()) return
        wars.forEach { war ->
            ZipFile(war.toFile()).use { zip ->
                val modelEntries = zip.entries().asSequence()
                    .map { it.name }
                    .filter { it.startsWith("WEB-INF/classes/models/") }
                    .toList()
                assertTrue(
                    modelEntries.isEmpty(),
                    "${war.fileName} must not contain WEB-INF/classes/models/: $modelEntries",
                )
            }
        }
    }

    @Test
    fun stagingWarCopiesOverlayAsOwnFileNotConcatenated() {
        val root = Path.of(System.getProperty("user.dir"))
        val pom = Files.readString(root.resolve("pom.xml"))
        val stagingSlice = pom.substringAfter("<id>staging-war</id>").substringBefore("<id>traffic-war</id>")
        assertTrue(
            stagingSlice.contains("tofile=\"\${project.build.outputDirectory}/application-subsystem.properties\""),
            stagingSlice,
        )
        assertTrue(
            !stagingSlice.contains("concat destfile=\"\${project.build.outputDirectory}/application-staging.properties\""),
            stagingSlice,
        )
    }

    private fun root(): Path = Path.of(System.getProperty("user.dir"))

    private fun profileProperties(pom: String, profileId: String): String {
        val afterId = pom.substringAfter("<id>$profileId</id>")
        return afterId.substringAfter("<properties>").substringBefore("</properties>")
    }
}
