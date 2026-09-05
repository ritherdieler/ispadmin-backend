package com.dscorp.wispadmin.wispadmin.config

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isRegularFile
import kotlin.io.path.relativeTo
import kotlin.streams.asSequence

class EventsPackageRulesTest {

    private val root: Path = Path.of(System.getProperty("user.dir"))
    private val mainKotlin: Path = root.resolve("src/main/kotlin")

    @Test
    fun eventsDoesNotImportDomainPackages() {
        val sourceRoot = mainKotlin.resolve("com/dscorp/wispadmin/events")
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
    fun applicationsScanEventsPackage() {
        val core = Files.readString(mainKotlin.resolve("com/dscorp/wispadmin/wispadmin/WispAdminApplication.kt"))
        val traffic = Files.readString(mainKotlin.resolve("com/dscorp/wispadmin/traffic/TrafficApplication.kt"))
        val gateway = Files.readString(mainKotlin.resolve("com/dscorp/wispadmin/oltgateway/OltGatewayApplication.kt"))
        val acs = Files.readString(mainKotlin.resolve("com/dscorp/wispadmin/acs/AcsApplication.kt"))
        assertTrue(core.contains("com.dscorp.wispadmin.events"), core)
        assertTrue(traffic.contains("com.dscorp.wispadmin.events"), traffic)
        assertTrue(gateway.contains("com.dscorp.wispadmin.events"), gateway)
        assertTrue(acs.contains("com.dscorp.wispadmin.events"), acs)
    }

    @Test
    fun coreDoesNotScanGenieAcsOrTr069ProfileAdmin() {
        val core = Files.readString(mainKotlin.resolve("com/dscorp/wispadmin/wispadmin/WispAdminApplication.kt"))
        assertTrue(core.contains("com\\\\.dscorp\\\\.wispadmin\\\\.acs\\\\..*"), core)
        assertTrue(core.contains("wispadmin\\\\.service\\\\.genieacs\\\\..*"), core)
        assertTrue(core.contains("Tr069ModelProfileController"), core)
    }
}
