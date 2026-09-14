package com.dscorp.wispadmin.architecture

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class MavenGoneTest {

    private val root: Path = Path.of(System.getProperty("user.dir"))

    @Test
    fun mavenBuildFilesDoNotExist() {
        assertFalse(Files.exists(root.resolve("pom.xml")), "pom.xml")
        assertFalse(Files.exists(root.resolve("mvnw")), "mvnw")
        assertFalse(Files.exists(root.resolve("mvnw.cmd")), "mvnw.cmd")
        assertFalse(Files.isDirectory(root.resolve(".mvn")), ".mvn")
    }

    @Test
    fun launchersAndAgentDocsDoNotInvokeMaven() {
        val files = listOf(
            "run-dev.sh",
            "run-prod.sh",
            "AGENTS.md",
            "README.md",
            ".agent-docs/gradle-modulos.md",
            "scripts/deploy.sh",
            "scripts/deploy-select-wars.sh",
            "scripts/snmp/optical-maxrep-ab.sh",
        )
        for (name in files) {
            val text = Files.readString(root.resolve(name))
            assertFalse(text.contains("mvnw"), name)
            assertFalse(text.contains("pom.xml"), name)
        }
        assertTrue(Files.readString(root.resolve("run-dev.sh")).contains("./gradlew"))
        assertTrue(Files.readString(root.resolve("run-prod.sh")).contains("./gradlew"))
        assertTrue(Files.readString(root.resolve("AGENTS.md")).contains("./gradlew"))
    }

    @Test
    fun mavenEraArchitectureTestsAreGone() {
        val gone = listOf(
            "app/src/test/kotlin/com/dscorp/wispadmin/architecture/SatelliteCompilationProfileTest.kt",
            "src/test/kotlin/com/dscorp/wispadmin/architecture/SatelliteCompilationProfileTest.kt",
            "core/src/test/kotlin/com/dscorp/wispadmin/wispadmin/config/WarSubsystemPackagingTest.kt",
            "src/test/kotlin/com/dscorp/wispadmin/wispadmin/config/WarSubsystemPackagingTest.kt",
        )
        for (name in gone) {
            assertFalse(Files.exists(root.resolve(name)), name)
        }
        assertNull(
            javaClass.classLoader.getResource(
                "com/dscorp/wispadmin/architecture/SatelliteCompilationProfileTest.class",
            ),
        )
    }
}
