package com.dscorp.wispadmin.wispadmin.config

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isRegularFile
import kotlin.io.path.relativeTo
import kotlin.streams.asSequence

class SubsystemDependencyRulesTest {

    private val root: Path = Path.of(System.getProperty("user.dir"))
    private val mainKotlin: Path = root.resolve("src/main/kotlin")

    private val optionalSubsystems = listOf(
        "observability",
        "oltgateway",
        "netdiag",
        "traffic",
        "servicehealth",
        "acs"
    )

    @Test
    fun coreDoesNotImportOptionalSubsystems() {
        assertNoForbiddenImports(
            sourcePackage = "wispadmin",
            forbidden = optionalSubsystems
        )
    }

    @Test
    fun netdiagDoesNotImportSiblingSubsystems() {
        assertNoForbiddenImports(
            sourcePackage = "netdiag",
            forbidden = listOf("oltgateway", "traffic", "servicehealth")
        )
    }

    @Test
    fun trafficDoesNotImportCoreDomain() {
        val violations = findForbiddenPrefixes(
            sourcePackage = "traffic",
            prefixes = listOf(
                "import com.dscorp.wispadmin.wispadmin.",
            ),
        )
        assertTrue(violations.isEmpty()) {
            "traffic no puede importar dominio JDBC del core:\n${violations.joinToString("\n")}"
        }
    }

    @Test
    fun trafficDoesNotImportSiblingSubsystems() {
        assertNoForbiddenImports(
            sourcePackage = "traffic",
            forbidden = listOf("netdiag", "oltgateway", "servicehealth")
        )
    }

    @Test
    fun oltgatewayDoesNotImportCoreDomain() {
        val violations = findForbiddenPrefixes(
            sourcePackage = "oltgateway",
            prefixes = listOf(
                "import com.dscorp.wispadmin.wispadmin.",
            ),
        )
        assertTrue(violations.isEmpty()) {
            "oltgateway no puede importar dominio JDBC del core:\n${violations.joinToString("\n")}"
        }
    }

    @Test
    fun oltgatewayDoesNotImportSiblingSubsystems() {
        assertNoForbiddenImports(
            sourcePackage = "oltgateway",
            forbidden = listOf("netdiag", "traffic", "servicehealth", "acs")
        )
    }

    @Test
    fun acsDoesNotImportCoreDomain() {
        val violations = findForbiddenPrefixes(
            sourcePackage = "acs",
            prefixes = listOf(
                "import com.dscorp.wispadmin.wispadmin.",
            ),
        )
        assertTrue(violations.isEmpty()) {
            "acs no puede importar dominio JDBC del core:\n${violations.joinToString("\n")}"
        }
    }

    @Test
    fun acsDoesNotImportSiblingSubsystems() {
        assertNoForbiddenImports(
            sourcePackage = "acs",
            forbidden = listOf("oltgateway", "netdiag", "traffic", "servicehealth")
        )
    }

    @Test
    fun servicehealthDoesNotImportSiblingSubsystems() {
        assertNoForbiddenImports(
            sourcePackage = "servicehealth",
            forbidden = listOf("observability", "oltgateway", "netdiag", "traffic")
        )
    }

    private fun assertNoForbiddenImports(sourcePackage: String, forbidden: List<String>) {
        val violations = findViolations(sourcePackage, forbidden)
        assertTrue(violations.isEmpty()) {
            "Imports cruzados prohibidos desde $sourcePackage:\n${violations.joinToString("\n")}"
        }
    }

    private fun findForbiddenPrefixes(sourcePackage: String, prefixes: List<String>): List<String> {
        val sourceRoot = mainKotlin.resolve("com/dscorp/wispadmin/$sourcePackage")
        if (!Files.isDirectory(sourceRoot)) return emptyList()
        return Files.walk(sourceRoot).asSequence()
            .filter { it.isRegularFile() && it.toString().endsWith(".kt") }
            .flatMap { file ->
                val relative = file.relativeTo(root)
                Files.readAllLines(file).asSequence()
                    .mapIndexedNotNull { index, line ->
                        val trimmed = line.trim()
                        if (prefixes.none { trimmed.startsWith(it) }) return@mapIndexedNotNull null
                        "$relative:${index + 1}: $trimmed"
                    }
            }
            .toList()
    }

    private fun findViolations(sourcePackage: String, forbidden: List<String>): List<String> {
        val sourceRoot = mainKotlin.resolve("com/dscorp/wispadmin/$sourcePackage")
        if (!Files.isDirectory(sourceRoot)) return emptyList()
        val importPrefixes = forbidden.map { "import com.dscorp.wispadmin.$it." }
        return Files.walk(sourceRoot).asSequence()
            .filter { it.isRegularFile() && it.toString().endsWith(".kt") }
            .flatMap { file ->
                val relative = file.relativeTo(root)
                Files.readAllLines(file).asSequence()
                    .mapIndexedNotNull { index, line ->
                        val trimmed = line.trim()
                        if (importPrefixes.none { trimmed.startsWith(it) }) return@mapIndexedNotNull null
                        val isPortContract = forbidden.any {
                            trimmed.startsWith("import com.dscorp.wispadmin.$it.port.")
                        }
                        if (isPortContract) return@mapIndexedNotNull null
                        "$relative:${index + 1}: $trimmed"
                    }
            }
            .toList()
    }
}
