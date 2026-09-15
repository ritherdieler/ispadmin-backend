package com.dscorp.wispadmin.wispadmin.config

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class StagingEnvironmentPropertiesTest {

    private fun staging(): String {
        val root = Path.of(System.getProperty("user.dir"))
        return Files.readString(root.resolve("core/src/main/resources/application-staging.properties"))
    }

    @Test
    fun staging_uses_own_context_path_and_schema_not_prod() {
        val staging = staging()
        assertTrue(staging.contains("server.servlet.context-path=/ispadmin-staging"), staging)
        assertFalse(staging.contains("spring.profiles.include="), staging)
        assertTrue(
            staging.contains("jdbc:mysql://mysql:3306/ispadmin_staging"),
            staging
        )
        assertTrue(staging.contains("spring.jpa.hibernate.ddl-auto=update"), staging)
        assertFalse(Regex("jdbc:mysql://mysql:3306/ispadmin\\?").containsMatchIn(staging), staging)
    }

    @Test
    fun staging_enables_360_collectors_and_keeps_operational_jobs_off() {
        val staging = staging()
        assertTrue(staging.contains("gigafiber.scheduling.enabled=true"), staging)
        assertTrue(staging.contains("gigafiber.scheduling.operational-jobs=false"), staging)
        assertTrue(staging.contains("gigafiber.environment.tag=stg"), staging)
        assertTrue(staging.contains("gigafiber.registration.timing.enabled=true"), staging)
        assertTrue(staging.contains("gigafiber.subsystems.servicehealth.enabled=true"), staging)
        assertTrue(staging.contains("gigafiber.subsystems.observability.enabled=false"), staging)
        assertTrue(staging.contains("service.health.enabled=true"), staging)
        assertTrue(staging.contains("service.health.optical-enabled=true"), staging)
        assertTrue(staging.contains("service.health.optical-pull-enabled=false"), staging)
        assertTrue(staging.contains("service.health.acs-enabled=true"), staging)
        assertTrue(staging.contains("service.health.actions-enabled=true"), staging)
        assertTrue(staging.contains("service.health.config-enabled=true"), staging)
        assertTrue(staging.contains("service.health.correlation-enabled=true"), staging)
        assertTrue(staging.contains("service.health.shared-incidents-enabled=true"), staging)
        assertTrue(staging.contains("service.health.shared-incident-notifications-enabled=false"), staging)
        assertTrue(staging.contains("acs.gateway.internal-base-url=http://127.0.0.1:8080/ispadmin-staging"), staging)
        assertTrue(staging.contains("acs.genieacs-to-acs-api-key=\${GENIEACS_TO_ACS_API_KEY:}"), staging)
        assertTrue(
            staging.contains("olt.gateway.acs-to-gateway-api-key=\${ACS_TO_GATEWAY_API_KEY:}"),
            staging,
        )
        assertTrue(staging.contains("olt.gateway.enabled=true"), staging)
        assertTrue(staging.contains("olt.gateway.writes.enabled=false"), staging)
        assertTrue(staging.contains("olt.gateway.sync.signal-enabled=true"), staging)
        assertTrue(staging.contains("olt.gateway.sync.inventory-enabled=false"), staging)
        assertTrue(staging.contains("olt.gateway.sync.alarm-enabled=false"), staging)
        assertTrue(staging.contains("olt.gateway.sync.lab-optical-ssh-enabled=false"), staging)
        assertTrue(staging.contains("olt.gateway.snmp.enabled=true"), staging)
        assertTrue(staging.contains("olt.gateway.snmp.trap.enabled=false"), staging)
        assertTrue(staging.contains("net.diag.enabled=false"), staging)
        assertTrue(staging.contains("net.diag.snmp.trap.udp-enabled=false"), staging)
        assertTrue(staging.contains("net.diag.syslog.udp-enabled=false"), staging)
        assertTrue(staging.contains("whatsapp.welcome-on-registration.enabled=false"), staging)
        assertTrue(staging.contains("whatsapp.retention.enabled=false"), staging)
        assertTrue(staging.contains("whatsapp.inbound-alert.enabled=false"), staging)
        assertTrue(staging.contains("crm.csat.enabled=false"), staging)
        assertTrue(staging.contains("traffic.internal-base-url=http://127.0.0.1:8080/ispadmin-staging"), staging)
        assertTrue(staging.contains("traffic.core-base-url=http://127.0.0.1:8080/ispadmin-staging"), staging)
        assertTrue(staging.contains("olt.gateway.internal-base-url=http://127.0.0.1:8080/ispadmin-staging"), staging)
        assertTrue(staging.contains("olt.gateway.client-enabled=true"), staging)
        assertTrue(staging.contains("acs.client-enabled=true"), staging)
        assertTrue(staging.contains("acs.internal-base-url=http://127.0.0.1:8080/ispadmin-staging"), staging)
        assertTrue(staging.contains("pppoe.migration.quarantine-days=7"), staging)
        assertTrue(staging.contains("gigafiber.redis.enabled=\${REDIS_ENABLED:true}"), staging)
        assertTrue(staging.contains("gigafiber.redis.host=\${REDIS_HOST:redis}"), staging)
    }

    @Test
    fun staging_enables_gateway_acs_client_for_tr069() {
        val staging = staging()
        assertTrue(
            Regex("""^olt\.gateway\.acs\.enabled=true\s*$""", RegexOption.MULTILINE).containsMatchIn(staging),
            staging,
        )
        assertTrue(
            staging.contains("olt.gateway.acs.internal-base-url=http://127.0.0.1:8080/ispadmin-staging"),
            staging,
        )
        assertTrue(staging.contains("olt.gateway.acs.api-key=\${ACS_API_KEY:dev-acs-key}"), staging)
        assertTrue(
            Regex("""^genieacs\.enabled=\$\{GENIEACS_ENABLED:true\}\s*$""", RegexOption.MULTILINE)
                .containsMatchIn(staging),
            staging,
        )
    }

    @Test
    fun staging_and_prod_use_satellite_datasources_in_the_single_war() {
        val root = Path.of(System.getProperty("user.dir"))
        val staging = Files.readString(root.resolve("core/src/main/resources/application-staging.properties"))
        val prod = Files.readString(root.resolve("core/src/main/resources/application-prod.properties"))
        assertTrue(staging.contains("jdbc:mysql://mysql:3306/stg_traffic?"), staging)
        assertTrue(staging.contains("jdbc:mysql://mysql:3306/stg_oltgateway?"), staging)
        assertTrue(staging.contains("jdbc:mysql://mysql:3306/stg_acs?"), staging)
        assertTrue(prod.contains("jdbc:mysql://mysql:3306/prod_traffic?"), prod)
        assertTrue(prod.contains("jdbc:mysql://mysql:3306/prod_oltgateway?"), prod)
        assertTrue(prod.contains("jdbc:mysql://mysql:3306/prod_acs?"), prod)
        listOf(
            "acs.datasource.password=\${ACS_DATASOURCE_PASSWORD:\${spring.datasource.password}}",
            "oltgateway.datasource.password=\${OLTGATEWAY_DATASOURCE_PASSWORD:\${spring.datasource.password}}",
            "traffic.datasource.password=\${TRAFFIC_DATASOURCE_PASSWORD:\${spring.datasource.password}}",
        ).forEach { line ->
            assertTrue(staging.contains(line), "staging missing $line")
            assertTrue(prod.contains(line), "prod missing $line")
        }
        assertFalse(staging.contains("spring.profiles.include="), staging)
    }

    @Test
    fun satellite_profiles_use_hibernate_update_not_prod_validate() {
        val root = Path.of(System.getProperty("user.dir"))
        listOf(
            "application-traffic.properties",
            "application-acs.properties",
            "application-oltgateway.properties",
        ).forEach { name ->
            val text = Files.readString(root.resolve("core/src/main/resources/$name"))
            assertTrue(
                Regex("""^spring\.jpa\.hibernate\.ddl-auto=update\s*$""", RegexOption.MULTILINE)
                    .containsMatchIn(text),
                "$name must not inherit prod ddl-auto=validate: $text",
            )
        }
    }

    @Test
    fun satellite_jpa_uses_hibernate_update() {
        val root = Path.of(System.getProperty("user.dir"))
        val prod = Files.readString(root.resolve("core/src/main/resources/application-prod.properties"))
        val staging = Files.readString(root.resolve("core/src/main/resources/application-staging.properties"))
        listOf("acs.jpa.hibernate.ddl-auto=update", "oltgateway.jpa.hibernate.ddl-auto=update", "traffic.jpa.hibernate.ddl-auto=update").forEach { line ->
            assertTrue(prod.contains(line), "$line missing in prod: $prod")
            assertTrue(staging.contains(line), "$line missing in staging: $staging")
        }
    }

    @Test
    fun satellite_profiles_override_spatial_dialect() {
        val root = Path.of(System.getProperty("user.dir"))
        val nonSpatial = "org.hibernate.dialect.MySQL57Dialect"
        listOf(
            "application-traffic.properties",
            "application-acs.properties",
            "application-oltgateway.properties",
        ).forEach { name ->
            val text = Files.readString(root.resolve("core/src/main/resources/$name"))
            assertTrue(
                text.contains("spring.jpa.properties.hibernate.dialect=$nonSpatial"),
                "$name must override spatial dialect (hibernate-spatial is excluded from satellite WARs): $text",
            )
            assertFalse(
                text.contains("MySQL56InnoDBSpatialDialect"),
                "$name must not keep spatial dialect: $text",
            )
        }
    }

    @Test
    fun traffic_profile_file_does_not_set_active_profiles() {
        val root = Path.of(System.getProperty("user.dir"))
        val traffic = Files.readString(root.resolve("core/src/main/resources/application-traffic.properties"))
        assertFalse(traffic.contains("spring.profiles.active="), traffic)
        assertTrue(traffic.contains("stg_traffic") || traffic.contains("prod_traffic"), traffic)
        assertTrue(traffic.contains("createDatabaseIfNotExist=true"), traffic)
        assertFalse(traffic.contains("spring.datasource.username="), traffic)
        assertFalse(traffic.contains("spring.datasource.password="), traffic)
    }

    @Test
    fun oltgateway_profile_file_does_not_set_active_profiles() {
        val root = Path.of(System.getProperty("user.dir"))
        val gateway = Files.readString(root.resolve("core/src/main/resources/application-oltgateway.properties"))
        assertFalse(gateway.contains("spring.profiles.active="), gateway)
        assertTrue(gateway.contains("stg_oltgateway") || gateway.contains("prod_oltgateway"), gateway)
        assertTrue(gateway.contains("createDatabaseIfNotExist=true"), gateway)
        assertTrue(gateway.contains("olt.gateway.enabled=true"), gateway)
        assertTrue(gateway.contains("olt.gateway.client-enabled=false"), gateway)
        assertFalse(gateway.contains("spring.datasource.username="), gateway)
        assertFalse(gateway.contains("spring.datasource.password="), gateway)
    }

    @Test
    fun prod_defaults_redis_disabled_via_env() {
        val root = Path.of(System.getProperty("user.dir"))
        val prod = Files.readString(root.resolve("core/src/main/resources/application-prod.properties"))
        assertTrue(prod.contains("gigafiber.redis.enabled=\${REDIS_ENABLED:false}"), prod)
        assertFalse(prod.contains("gigafiber.redis.enabled=false\n"), prod)
    }

    @Test
    fun staging_enables_redis_for_the_single_war() {
        val root = Path.of(System.getProperty("user.dir"))
        val staging = Files.readString(root.resolve("core/src/main/resources/application-staging.properties"))
        assertTrue(staging.contains("gigafiber.redis.enabled=\${REDIS_ENABLED:true}"), staging)
        assertTrue(staging.contains("gigafiber.redis.host=\${REDIS_HOST:redis}"), staging)
        assertTrue(staging.contains("gigafiber.redis.namespace=stg"), staging)
        assertTrue(staging.contains("olt.gateway.writes.enabled=false"), staging)
    }

    @Test
    fun traffic_security_permits_actuator_health() {
        val src = Files.readString(
            Path.of(System.getProperty("user.dir"))
                .resolve("traffic/src/main/kotlin/com/dscorp/wispadmin/traffic/config/TrafficSecurityConfig.kt"),
        )
        assertTrue(src.contains("/actuator/health"), src)
        assertTrue(src.contains("permitAll()"), src)
    }

    @Test
    fun satellite_profiles_disable_spring_redis_health() {
        val root = Path.of(System.getProperty("user.dir"))
        listOf("application-traffic.properties", "application-oltgateway.properties", "application-acs.properties").forEach { name ->
            val text = Files.readString(root.resolve("core/src/main/resources/$name"))
            assertTrue(
                Regex("""^management\.health\.redis\.enabled=false\s*$""", RegexOption.MULTILINE).containsMatchIn(text),
                "$name must not fail actuator because Spring Redis auto-config is unused: $text",
            )
        }
    }

    @Test
    fun acs_flyway_owns_its_schema_not_core_catalog() {
        val root = Path.of(System.getProperty("user.dir"))
        val prod = Files.readString(root.resolve("core/src/main/resources/application-prod.properties"))
        val staging = Files.readString(root.resolve("core/src/main/resources/application-staging.properties"))
        val persistence = Files.readString(
            root.resolve("acs/src/main/kotlin/com/dscorp/wispadmin/acs/config/AcsPersistenceConfig.kt"),
        )
        assertTrue(prod.contains("acs.flyway.enabled=true"), prod)
        assertTrue(staging.contains("acs.flyway.enabled=true"), staging)
        assertTrue(persistence.contains("classpath:db/acs"), persistence)
        assertFalse(prod.contains("acs.profiles.catalog"), prod)
        assertFalse(staging.contains("acs.profiles.catalog"), staging)
        assertTrue(staging.contains("gigafiber.redis.namespace=stg"), staging)
        val acsProps = Files.readString(root.resolve("core/src/main/resources/application-acs.properties"))
        assertTrue(
            Regex("""^genieacs\.vparams\.enabled=\$\{GENIEACS_VPARAMS_ENABLED:false\}\s*$""", RegexOption.MULTILINE)
                .containsMatchIn(acsProps),
            acsProps,
        )
    }

    @Test
    fun acs_secret_names_are_catalogued_without_values() {
        val root = Path.of(System.getProperty("user.dir"))
        val secrets = Files.readString(root.resolve(".agent-docs/vps-secrets-management.md"))
        val example = Files.readString(root.resolve("scripts/deploy.config.example"))
        assertTrue(secrets.contains("ACS_API_KEY"), secrets)
        assertTrue(example.contains("ACS_API_KEY"), example)
        assertFalse(Regex("ACS_API_KEY=\\S+").containsMatchIn(example), example)
    }
}
