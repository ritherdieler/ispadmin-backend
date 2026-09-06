package com.dscorp.wispadmin.architecture

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isRegularFile
import kotlin.io.path.relativeTo
import kotlin.streams.asSequence

class CrossSchemaJdbcForbiddenTest {

    private val root: Path = Path.of(System.getProperty("user.dir"))

    private val qualifiedSchema = Regex(
        """(?<![A-Za-z])(?:ispadmin|ispadmin_staging|stg_acs|prod_acs|stg_oltgateway|prod_oltgateway|stg_traffic|prod_traffic)\.[A-Za-z_]""",
    )

    private val catalogLeak = Regex("""acs\.profiles\.catalog|ACS_PROFILES_CATALOG|`${'$'}catalog`""")

    private val oneShotAllowlist = setOf(
        "scripts/sql/copy-tr069-profiles-to-acs.sql",
        "scripts/sql/migrate-olt-mgr-to-stg_oltgateway.sql",
        "scripts/sql/migrate-olt-mgr-to-prod_oltgateway.sql",
        "scripts/sql/migrate-traffic-to-stg_traffic.sql",
        "scripts/sql/migrate-traffic-to-prod_traffic.sql",
        "scripts/sql/staging-e2e-registration-catalog.sql",
        "scripts/sql/staging-e2e-place-nap.sql",
        "scripts/sql/staging-ip-pool.sql",
        "scripts/whatsapp-normalize-message-log-phones.sql",
    )

    @Test
    fun productionKotlinDoesNotQualifyAnotherWarSchema() {
        val violations = scan(root.resolve("src/main/kotlin")) { path ->
            path.toString().endsWith(".kt")
        }
        assertTrue(violations.isEmpty()) {
            "JDBC cruzado en runtime (Kotlin). Un WAR solo usa su datasource, tablas sin schema ajeno:\n${violations.joinToString("\n")}"
        }
    }

    @Test
    fun flywayDoesNotJoinForeignWarSchemas() {
        val violations = scan(root.resolve("src/main/resources/db")) { path ->
            path.toString().endsWith(".sql")
        }
        assertTrue(violations.isEmpty()) {
            "Flyway no puede nombrar schemas de otro WAR:\n${violations.joinToString("\n")}"
        }
    }

    @Test
    fun applicationPropertiesDoNotPointAcsAtCoreCatalog() {
        val acs = Files.readString(root.resolve("src/main/resources/application-acs.properties"))
        assertTrue(!catalogLeak.containsMatchIn(acs), acs)
        val pom = Files.readString(root.resolve("pom.xml"))
        assertTrue(!pom.contains("acs.profiles.catalog"), pom)
        assertTrue(!pom.contains("ACS_PROFILES_CATALOG"), pom)
    }

    @Test
    fun oneShotCrossSchemaSqlStaysAllowlisted() {
        val hits = mutableListOf<String>()
        val sqlRoot = root.resolve("scripts")
        Files.walk(sqlRoot).use { stream ->
            stream.asSequence()
                .filter { it.isRegularFile() }
                .filter { it.toString().endsWith(".sql") }
                .forEach { file ->
                    val text = Files.readString(file)
                    if (!qualifiedSchema.containsMatchIn(text)) return@forEach
                    val relative = file.relativeTo(root).toString().replace('\\', '/')
                    if (relative !in oneShotAllowlist) {
                        hits.add(relative)
                    }
                }
        }
        assertTrue(hits.isEmpty()) {
            "SQL one-shot con schema calificado nuevo; documentarlo o no usarlo. Archivos:\n${hits.joinToString("\n")}"
        }
    }

    private fun scan(start: Path, accept: (Path) -> Boolean): List<String> {
        if (!Files.isDirectory(start)) return emptyList()
        return Files.walk(start).use { stream ->
            stream.asSequence()
                .filter { it.isRegularFile() && accept(it) }
                .flatMap { file ->
                    val relative = file.relativeTo(root)
                    Files.readAllLines(file).asSequence().mapIndexedNotNull { index, line ->
                        if (!qualifiedSchema.containsMatchIn(line) && !catalogLeak.containsMatchIn(line)) {
                            return@mapIndexedNotNull null
                        }
                        "$relative:${index + 1}: ${line.trim()}"
                    }
                }
                .toList()
        }
    }
}
