package com.dscorp.wispadmin.wispadmin.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.time.Instant

class DeployWarNeedsRebuildScriptTest {

    @TempDir
    lateinit var tmp: Path

    @Test
    fun missingWarNeedsRebuild() {
        val project = layoutProject()
        val war = tmp.resolve("missing.war")
        val out = runNeeds("oltgateway", war, project)
        assertEquals(0, out.exit, out.combined)
        assertTrue(out.combined.contains("missing"), out.combined)
    }

    @Test
    fun freshWarSkipsRebuild() {
        val project = layoutProject()
        val war = tmp.resolve("fresh.war")
        Files.writeString(war, "war")
        touchOlder(project.resolve("src/main/kotlin/com/dscorp/wispadmin/oltgateway/A.kt"), war)
        touchOlder(project.resolve("pom.xml"), war)
        val out = runNeeds("oltgateway", war, project)
        assertEquals(1, out.exit, out.combined)
        assertTrue(out.combined.contains("skip-rebuild"), out.combined)
    }

    @Test
    fun newerSourceNeedsRebuild() {
        val project = layoutProject()
        val war = tmp.resolve("stale.war")
        Files.writeString(war, "war")
        val source = project.resolve("src/main/kotlin/com/dscorp/wispadmin/oltgateway/A.kt")
        touchOlder(source, war)
        Files.setLastModifiedTime(source, FileTime.from(Instant.now().plusSeconds(60)))
        val out = runNeeds("oltgateway", war, project)
        assertEquals(0, out.exit, out.combined)
        assertTrue(out.combined.contains("stale input"), out.combined)
    }

    @Test
    fun forceEnvNeedsRebuildEvenIfFresh() {
        val project = layoutProject()
        val war = tmp.resolve("fresh.war")
        Files.writeString(war, "war")
        touchOlder(project.resolve("src/main/kotlin/com/dscorp/wispadmin/oltgateway/A.kt"), war)
        touchOlder(project.resolve("pom.xml"), war)
        val out = runNeeds("oltgateway", war, project, env = mapOf("FORCE_WAR_REBUILD" to "1"))
        assertEquals(0, out.exit, out.combined)
        assertTrue(out.combined.contains("FORCE_WAR_REBUILD"), out.combined)
    }

    private fun layoutProject(): Path {
        val project = tmp.resolve("proj")
        val gw = project.resolve("src/main/kotlin/com/dscorp/wispadmin/oltgateway")
        Files.createDirectories(gw)
        Files.createDirectories(project.resolve("src/main/kotlin/com/dscorp/wispadmin/events"))
        Files.createDirectories(project.resolve("src/main/kotlin/com/dscorp/wispadmin/transport"))
        Files.createDirectories(project.resolve("src/main/resources"))
        Files.writeString(gw.resolve("A.kt"), "class A")
        Files.writeString(project.resolve("pom.xml"), "<project/>")
        Files.writeString(project.resolve("src/main/resources/application-oltgateway.properties"), "x=1")
        return project
    }

    private fun touchOlder(path: Path, war: Path) {
        Files.createDirectories(path.parent)
        if (!Files.exists(path)) {
            Files.writeString(path, "x")
        }
        val older = Files.getLastModifiedTime(war).toInstant().minusSeconds(120)
        Files.setLastModifiedTime(path, FileTime.from(older))
    }

    private data class Proc(val exit: Int, val combined: String)

    private fun runNeeds(
        key: String,
        war: Path,
        project: Path,
        env: Map<String, String> = emptyMap(),
    ): Proc {
        val root = Path.of(System.getProperty("user.dir"))
        val script = root.resolve("scripts/deploy-war-needs-rebuild.sh").toFile()
        val pb = ProcessBuilder("bash", script.absolutePath, key, war.toString(), project.toString())
            .directory(root.toFile())
            .redirectErrorStream(true)
        pb.environment().remove("FORCE_WAR_REBUILD")
        pb.environment().putAll(env)
        val process = pb.start()
        val output = process.inputStream.bufferedReader().readText()
        return Proc(process.waitFor(), output)
    }
}
