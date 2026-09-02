package com.dscorp.wispadmin.wispadmin.config

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

/**
 * Las aserciones comprueban la forma de la clave (placeholder de entorno), nunca el valor,
 * para no reintroducir el secreto en el repositorio.
 */
class OltServiceSecretsPropertiesFileTest {

    private val root = Path.of(System.getProperty("user.dir"))

    private fun read(relative: String): String = Files.readString(root.resolve(relative))

    @Test
    fun applicationProd_toma_la_api_key_de_smartolt_del_entorno() {
        val prod = read("src/main/resources/application-prod.properties")

        assertTrue(
            Regex("""^olt\.service\.api-key=\$\{OLT_SERVICE_API_KEY""", RegexOption.MULTILINE)
                .containsMatchIn(prod),
            "application-prod.properties debe leer olt.service.api-key del entorno"
        )
        assertFalse(
            Regex("""^olt\.service\.api-key=[0-9a-fA-F]{16,}\s*$""", RegexOption.MULTILINE)
                .containsMatchIn(prod),
            "application-prod.properties no debe llevar la api key literal"
        )
    }

    @Test
    fun applicationProd_toma_la_password_de_mysql_del_entorno() {
        val prod = read("src/main/resources/application-prod.properties")

        assertTrue(
            Regex("""^spring\.datasource\.password=\$\{DB_PASSWORD""", RegexOption.MULTILINE)
                .containsMatchIn(prod),
            "application-prod.properties debe leer spring.datasource.password del entorno"
        )
    }

    @Test
    fun applicationDev_toma_la_api_key_de_smartolt_del_entorno() {
        val dev = read("src/main/resources/application-dev.properties")

        assertTrue(
            Regex("""^olt\.service\.api-key=\$\{OLT_SERVICE_API_KEY""", RegexOption.MULTILINE)
                .containsMatchIn(dev),
            "application-dev.properties debe leer olt.service.api-key del entorno"
        )
    }

    @Test
    fun no_queda_ninguna_credencial_de_smartolt_compilada_en_el_codigo() {
        val configConstants = root.resolve(
            "src/main/kotlin/com/dscorp/wispadmin/wispadmin/config/ConfigConstants.kt"
        )
        if (Files.exists(configConstants)) {
            val content = Files.readString(configConstants)
            assertFalse(
                content.contains("OLT_SERVICE_API_KEY"),
                "ConfigConstants.kt no debe declarar la api key de SmartOLT"
            )
        }

        val kotlinRoot = root.resolve("src/main/kotlin")
        val offenders = Files.walk(kotlinRoot).use { paths ->
            paths.filter { Files.isRegularFile(it) && it.toString().endsWith(".kt") }
                .filter { file ->
                    Regex("""const\s+val\s+\w*API_KEY\w*\s*(:\s*String\s*)?=\s*"[0-9a-fA-F]{16,}"""")
                        .containsMatchIn(Files.readString(file))
                }
                .map { kotlinRoot.relativize(it).toString() }
                .toList()
        }

        assertTrue(offenders.isEmpty(), "hay api keys compiladas en: $offenders")
    }
}
