package com.dscorp.wispadmin.wispadmin.config

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isRegularFile
import kotlin.io.path.relativeTo
import kotlin.streams.asSequence

class EventsPackageRulesTest {

    private val root: Path = Path.of(System.getProperty("user.dir"))
    private val eventsKotlin: Path = root.resolve("shared/src/main/kotlin")

    @Test
    fun eventsDoesNotImportDomainPackages() {
        val sourceRoot = eventsKotlin.resolve("com/dscorp/wispadmin/events")
        assertTrue(Files.isDirectory(sourceRoot), "missing events package")
        val forbidden = listOf(
            "import com.dscorp.wispadmin.wispadmin.",
            "import com.dscorp.wispadmin.traffic.",
            "import com.dscorp.wispadmin.oltgateway.",
            "import com.dscorp.wispadmin.servicehealth.",
            "import com.dscorp.wispadmin.netdiag.",
        )
        val violations = Files.walk(sourceRoot).asSequence()
            .filter { it.isRegularFile() && it.toString().endsWith(".kt") }
            .flatMap { file ->
                val relative = file.relativeTo(root)
                Files.readAllLines(file).asSequence()
                    .mapIndexedNotNull { index, line ->
                        val trimmed = line.trim()
                        if (forbidden.none { trimmed.startsWith(it) }) return@mapIndexedNotNull null
                        "$relative:${index + 1}: $trimmed"
                    }
            }
            .toList()
        assertTrue(violations.isEmpty()) {
            "events no puede importar dominio de otros paquetes:\n${violations.joinToString("\n")}"
        }
    }

    @Test
    fun applicationScansEventsPackage() {
        val core = Files.readString(
            root.resolve("core/src/main/kotlin/com/dscorp/wispadmin/wispadmin/WispAdminApplication.kt"),
        )
        assertTrue(core.contains("com.dscorp.wispadmin.events"), core)
    }

    @Test
    fun coreScansAcsAndExcludesLegacyGenieAcsService() {
        val core = Files.readString(
            root.resolve("core/src/main/kotlin/com/dscorp/wispadmin/wispadmin/WispAdminApplication.kt"),
        )
        assertTrue(core.contains("com.dscorp.wispadmin.acs"), core)
        assertTrue(core.contains("genieacs"), core)
        assertTrue(core.contains("FilterType.REGEX"), core)
        assertFalse(core.contains("controller\\.Tr069ModelProfileController"), core)
    }
}
