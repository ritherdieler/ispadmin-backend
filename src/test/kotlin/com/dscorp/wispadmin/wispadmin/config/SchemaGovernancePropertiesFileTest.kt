package com.dscorp.wispadmin.wispadmin.config

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class SchemaGovernancePropertiesFileTest {

    private val root = Path.of(System.getProperty("user.dir"))

    private fun read(relative: String): String = Files.readString(root.resolve(relative))

    @Test
    fun applicationProd_valida_el_esquema_en_vez_de_alterarlo() {
        val prod = read("src/main/resources/application-prod.properties")
        assertTrue(
            Regex("""^spring\.jpa\.hibernate\.ddl-auto=validate\s*$""", RegexOption.MULTILINE)
                .containsMatchIn(prod),
            "producción debe usar ddl-auto=validate"
        )
        assertTrue(
            Regex("""^spring\.flyway\.enabled=true\s*$""", RegexOption.MULTILINE)
                .containsMatchIn(prod)
        )
        assertTrue(
            Regex("""^spring\.flyway\.baseline-on-migrate=true\s*$""", RegexOption.MULTILINE)
                .containsMatchIn(prod)
        )
        assertTrue(
            Regex("""^spring\.flyway\.baseline-version=38\s*$""", RegexOption.MULTILINE)
                .containsMatchIn(prod)
        )
    }

    @Test
    fun applicationDev_tambien_activa_flyway_con_el_mismo_baseline() {
        val dev = read("src/main/resources/application-dev.properties")
        assertTrue(
            Regex("""^spring\.flyway\.enabled=true\s*$""", RegexOption.MULTILINE)
                .containsMatchIn(dev)
        )
        assertTrue(
            Regex("""^spring\.flyway\.baseline-version=38\s*$""", RegexOption.MULTILINE)
                .containsMatchIn(dev)
        )
    }

    @Test
    fun no_hay_dos_migraciones_con_la_misma_version() {
        val versions = Files.list(root.resolve("src/main/resources/db/migration")).use { paths ->
            paths.map { it.fileName.toString() }
                .filter { it.startsWith("V") && it.endsWith(".sql") }
                .map { it.substringBefore("__") }
                .toList()
        }
        val duplicates = versions.groupingBy { it }.eachCount().filter { it.value > 1 }
        assertTrue(duplicates.isEmpty(), "versiones Flyway duplicadas: $duplicates")
    }

    @Test
    fun el_datasource_de_telemetria_esta_declarado_aparte() {
        val prod = read("src/main/resources/application-prod.properties")
        assertTrue(
            Regex("""^telemetry\.datasource\.url=""", RegexOption.MULTILINE)
                .containsMatchIn(prod)
        )
        assertTrue(
            Regex("""^telemetry\.datasource\.username=""", RegexOption.MULTILINE)
                .containsMatchIn(prod)
        )
    }
}
