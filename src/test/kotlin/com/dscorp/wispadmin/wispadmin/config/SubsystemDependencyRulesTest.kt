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

    @Test
    fun coreShippedCodeDoesNotReferenceSplitWarTypes() {
        val shipped = listOf(
            "shared",
            "wispadmin",
            "events",
            "transport",
            "netdiag",
            "servicehealth",
            "observability",
        )
        val pattern = Regex("""com\.dscorp\.wispadmin\.(traffic|oltgateway|acs)\.""")
        val violations = shipped.flatMap { pkg ->
            val sourceRoot = mainKotlin.resolve("com/dscorp/wispadmin/$pkg")
            if (!Files.isDirectory(sourceRoot)) return@flatMap emptyList()
            Files.walk(sourceRoot).asSequence()
                .filter { it.isRegularFile() && it.toString().endsWith(".kt") }
                .flatMap { file ->
                    val relative = file.relativeTo(root)
                    Files.readAllLines(file).asSequence().mapIndexedNotNull { index, line ->
                        if (!pattern.containsMatchIn(line)) return@mapIndexedNotNull null
                        "$relative:${index + 1}: ${line.trim()}"
                    }
                }
                .toList()
        }
        assertTrue(violations.isEmpty()) {
            "El core no puede referenciar tipos de wars split (traffic, oltgateway, acs):\n${violations.joinToString("\n")}"
        }
    }

    @Test
    fun embeddedModulesImportCoreOnlyThroughAllowlistedAdapters() {
        val allowed = setOf(
            "src/main/kotlin/com/dscorp/wispadmin/servicehealth/config/ServiceHealthScope.kt",
            "src/main/kotlin/com/dscorp/wispadmin/servicehealth/controller/HealthAccess.kt",
            "src/main/kotlin/com/dscorp/wispadmin/servicehealth/service/AcsTelemetryService.kt",
            "src/main/kotlin/com/dscorp/wispadmin/servicehealth/service/DiagnosisEngine.kt",
            "src/main/kotlin/com/dscorp/wispadmin/servicehealth/service/HealthEvidenceReader.kt",
            "src/main/kotlin/com/dscorp/wispadmin/servicehealth/service/IdentityChangeObserver.kt",
            "src/main/kotlin/com/dscorp/wispadmin/servicehealth/service/IdentityService.kt",
            "src/main/kotlin/com/dscorp/wispadmin/servicehealth/service/RemoteActionService.kt",
            "src/main/kotlin/com/dscorp/wispadmin/servicehealth/service/ServiceHealthSubscriptionContextReader.kt",
            "src/main/kotlin/com/dscorp/wispadmin/netdiag/adapter/NetDiagDeviceDirectoryAdapter.kt",
            "src/main/kotlin/com/dscorp/wispadmin/netdiag/adapter/WispAdminOntSubscriptionAdapter.kt",
            "src/main/kotlin/com/dscorp/wispadmin/netdiag/adapter/WispAdminRadiusImpactAdapter.kt",
            "src/main/kotlin/com/dscorp/wispadmin/netdiag/service/WhatsAppOpsNotifier.kt",
            "src/main/kotlin/com/dscorp/wispadmin/observability/config/ObservabilityApiKeyFilter.kt",
            "src/main/kotlin/com/dscorp/wispadmin/observability/config/ObservabilityWebSocketHandshakeInterceptor.kt",
            "src/main/kotlin/com/dscorp/wispadmin/observability/config/TraceContextFilter.kt",
            "src/main/kotlin/com/dscorp/wispadmin/observability/controller/ObservabilityEventController.kt",
            "src/main/kotlin/com/dscorp/wispadmin/observability/service/InProcessObservabilityReporter.kt",
            "src/main/kotlin/com/dscorp/wispadmin/observability/service/ObsIngestionService.kt",
            "src/main/kotlin/com/dscorp/wispadmin/observability/tracing/ObsTracer.kt",
            "src/main/kotlin/com/dscorp/wispadmin/observability/tracing/TracingClientHttpRequestInterceptor.kt",
        )
        val actual = listOf("servicehealth", "netdiag", "observability").flatMap { pkg ->
            findForbiddenPrefixes(
                sourcePackage = pkg,
                prefixes = listOf("import com.dscorp.wispadmin.wispadmin."),
            ).map { it.substringBefore(':') }
        }.toSet()
        val unexpected = actual - allowed
        val stale = allowed - actual
        assertTrue(unexpected.isEmpty()) {
            "Nuevos imports al core fuera de adapters embebidos:\n${unexpected.joinToString("\n")}"
        }
        assertTrue(stale.isEmpty()) {
            "Allowlist H8 desactualizada; quitar archivos que ya no importan core:\n${stale.joinToString("\n")}"
        }
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
        val splitWars = setOf("oltgateway", "traffic", "acs")
        return Files.walk(sourceRoot).asSequence()
            .filter { it.isRegularFile() && it.toString().endsWith(".kt") }
            .flatMap { file ->
                val relative = file.relativeTo(root)
                Files.readAllLines(file).asSequence()
                    .mapIndexedNotNull { index, line ->
                        val trimmed = line.trim()
                        if (importPrefixes.none { trimmed.startsWith(it) }) return@mapIndexedNotNull null
                        val portTarget = forbidden.firstOrNull {
                            trimmed.startsWith("import com.dscorp.wispadmin.$it.port.")
                        }
                        if (portTarget != null) {
                            val coreUsingSplitPort =
                                sourcePackage == "wispadmin" && portTarget in splitWars
                            if (!coreUsingSplitPort) return@mapIndexedNotNull null
                        }
                        "$relative:${index + 1}: $trimmed"
                    }
            }
            .toList()
    }
}
