package com.dscorp.wispadmin.wispadmin.config

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class LocalPrestagingEnvironmentPropertiesTest {

    private val root: Path = Path.of(System.getProperty("user.dir"))

    private fun prestaging(): String {
        return Files.readString(root.resolve("core/src/main/resources/application-local-prestaging.properties"))
    }

    @Test
    fun prestaging_uses_lpstg_tag_lan_olt_and_local_acs() {
        val prestaging = prestaging()
        assertTrue(prestaging.contains("gigafiber.environment.tag=lpstg"), prestaging)
        assertTrue(
            Regex("""^gigafiber\.redis\.enabled=true\s*$""", RegexOption.MULTILINE).containsMatchIn(prestaging),
            prestaging,
        )
        assertTrue(
            Regex("""^gigafiber\.redis\.namespace=lpstg\s*$""", RegexOption.MULTILINE).containsMatchIn(prestaging),
            prestaging,
        )
        assertTrue(
            Regex("""^gigafiber\.scheduling\.enabled=false\s*$""", RegexOption.MULTILINE).containsMatchIn(prestaging),
            prestaging,
        )
        assertTrue(prestaging.contains("olt.gateway.host=10.11.104.2"), prestaging)
        assertTrue(
            Regex("""^olt\.provider\.authorize=GATEWAY\s*$""", RegexOption.MULTILINE).containsMatchIn(prestaging),
            prestaging,
        )
        assertTrue(
            Regex("""^olt\.gateway\.writes\.enabled=true\s*$""", RegexOption.MULTILINE).containsMatchIn(prestaging),
            prestaging,
        )
        assertTrue(
            prestaging.contains("olt.gateway.acs.internal-base-url=http://127.0.0.1:8082/ispadmin"),
            prestaging,
        )
        assertTrue(
            prestaging.contains("olt.gateway.internal-base-url=http://127.0.0.1:8082/ispadmin"),
            prestaging,
        )
        assertTrue(
            Regex("""^genieacs\.nbi-base-url=http://127\.0\.0\.1:7557\s*$""", RegexOption.MULTILINE)
                .containsMatchIn(prestaging),
            prestaging,
        )
        assertTrue(prestaging.contains("jdbc:mysql://localhost:3306/ispadmin_prestaging"), prestaging)
        assertTrue(prestaging.contains("jdbc:mysql://localhost:3306/prestaging_oltgateway"), prestaging)
        assertTrue(prestaging.contains("jdbc:mysql://localhost:3306/prestaging_acs"), prestaging)
        assertTrue(prestaging.contains("gigafiber.registration.timing.enabled=true"), prestaging)
        assertTrue(
            Regex("""^mikrotik\.connection\.mock\.enabled=false\s*$""", RegexOption.MULTILINE)
                .containsMatchIn(prestaging),
            prestaging,
        )
        assertTrue(
            Regex("""^olt\.service\.mock\.enabled=false\s*$""", RegexOption.MULTILINE)
                .containsMatchIn(prestaging),
            prestaging,
        )
        assertFalse(
            Regex("""^mikrotik\.connection\.override\.ip=.+$""", RegexOption.MULTILINE)
                .containsMatchIn(prestaging),
            prestaging,
        )
    }

    @Test
    fun prestaging_does_not_use_vps_staging_or_prod_jdbc() {
        val prestaging = prestaging()
        assertFalse(prestaging.contains("8091"), prestaging)
        assertFalse(prestaging.contains("ispadmin-staging-acs"), prestaging)
        assertFalse(prestaging.contains("212.85.13.47"), prestaging)
        assertFalse(prestaging.contains("jdbc:mysql://mysql"), prestaging)
        assertFalse(prestaging.contains("prod_acs"), prestaging)
        assertFalse(prestaging.contains("prod_oltgateway"), prestaging)
        assertFalse(prestaging.contains("stg_acs"), prestaging)
        assertFalse(prestaging.contains("stg_oltgateway"), prestaging)
        assertFalse(prestaging.contains("ispadmin_staging"), prestaging)
        val authorizeLines = prestaging.lineSequence()
            .filter { it.trim().startsWith("olt.provider.authorize=") }
            .toList()
        assertTrue(authorizeLines.isNotEmpty(), prestaging)
        authorizeLines.forEach { line ->
            assertFalse(line.contains("SMARTOLT"), line)
        }
    }

    @Test
    fun prestaging_does_not_embed_passwords() {
        val prestaging = prestaging()
        val rawSecret = Regex(
            """^(spring\.datasource\.password|olt\.gateway\.password|olt\.gateway\.api-key|acs\.api-key)=(?!\$\{).+""",
            RegexOption.MULTILINE,
        )
        assertFalse(rawSecret.containsMatchIn(prestaging), prestaging)
        assertTrue(
            prestaging.contains("spring.config.import=optional:classpath:application-local-prestaging.secrets.properties"),
            prestaging,
        )
    }

    @Test
    fun other_env_files_do_not_contain_prestaging_topology() {
        val forbidden = listOf(
            "ispadmin_prestaging",
            "prestaging_acs",
            "prestaging_oltgateway",
            "gigafiber.environment.tag=lpstg",
            "http://127.0.0.1:8090/ispadmin-acs",
        )
        listOf(
            "application-dev.properties",
            "application-staging.properties",
            "application-prod.properties",
            "application-acs.properties",
            "application-oltgateway.properties",
        ).forEach { name ->
            val text = Files.readString(root.resolve("core/src/main/resources/$name"))
            forbidden.forEach { token ->
                assertFalse(text.contains(token), "$name must not contain $token")
            }
        }
    }

    @Test
    fun satellite_entrypoints_are_configuration_not_boot_apps() {
        val application = Files.readString(root.resolve("core/src/main/resources/application.properties"))
        assertTrue(
            Regex("""^spring\.profiles\.active=dev,local\s*$""", RegexOption.MULTILINE).containsMatchIn(application),
            application,
        )
        val gateway = Files.readString(
            root.resolve("oltgateway/src/main/kotlin/com/dscorp/wispadmin/oltgateway/OltGatewayApplication.kt"),
        )
        val acs = Files.readString(
            root.resolve("acs/src/main/kotlin/com/dscorp/wispadmin/acs/AcsApplication.kt"),
        )
        assertTrue(gateway.contains("@Configuration"), gateway)
        assertTrue(acs.contains("@Configuration"), acs)
        assertFalse(gateway.contains("@SpringBootApplication"), gateway)
        assertFalse(acs.contains("@SpringBootApplication"), acs)
    }

    @Test
    fun secrets_overlay_is_gitignored_and_example_has_no_values() {
        val gitignore = Files.readString(root.resolve(".gitignore"))
        assertTrue(
            gitignore.lineSequence().any { it.trim() == "/core/src/main/resources/application-local-prestaging.secrets.properties" },
            gitignore,
        )
        val example = Files.readString(
            root.resolve("core/src/main/resources/application-local-prestaging.secrets.properties.example"),
        )
        assertTrue(example.contains("spring.datasource.password="), example)
        assertTrue(example.contains("olt.gateway.password="), example)
        example.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .forEach { line ->
                val eq = line.indexOf('=')
                assertTrue(eq > 0, line)
                assertTrue(line.substring(eq + 1).isEmpty(), line)
            }
    }

    @Test
    fun opt_in_script_activates_local_prestaging_without_changing_vps_defaults() {
        val script = Files.readString(root.resolve("scripts/run-local-prestaging.sh"))
        assertTrue(script.contains(":core:bootRun"), script)
        assertTrue(script.contains("dev,local-prestaging"), script)
        assertTrue(script.contains("8082"), script)
        assertTrue(script.contains("free_olt_ssh"), script)
        assertTrue(script.contains("free-olt-ssh"), script)
        assertTrue(script.contains("ensure_redis"), script)
        assertTrue(script.contains("inform-notify"), script)
        assertTrue(script.contains("redis-local.sh"), script)
        assertTrue(script.contains("10.11.104.2"), script)
        assertFalse(script.contains("AcsApplicationKt"), script)
        assertFalse(script.contains("OltGatewayApplicationKt"), script)
        assertFalse(script.contains("mvnw"), script)
        assertFalse(script.contains("8091"), script)
        assertFalse(script.contains("ispadmin-staging-acs"), script)
    }
}
