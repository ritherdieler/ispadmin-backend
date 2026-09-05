package com.dscorp.wispadmin.wispadmin.config

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class StagingEnvironmentPropertiesTest {

    private fun staging(): String {
        val root = Path.of(System.getProperty("user.dir"))
        return Files.readString(root.resolve("src/main/resources/application-staging.properties"))
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
    fun staging_disables_collectors_whatsapp_and_udp_binds_by_default() {
        val staging = staging()
        assertTrue(staging.contains("gigafiber.scheduling.enabled=false"), staging)
        assertTrue(staging.contains("gigafiber.environment.tag=stg"), staging)
        assertTrue(staging.contains("gigafiber.subsystems.servicehealth.enabled=false"), staging)
        assertTrue(staging.contains("gigafiber.subsystems.observability.enabled=false"), staging)
        assertTrue(staging.contains("service.health.enabled=false"), staging)
        assertTrue(staging.contains("service.health.optical-enabled=false"), staging)
        assertTrue(staging.contains("service.health.acs-enabled=false"), staging)
        assertTrue(staging.contains("olt.gateway.enabled=false"), staging)
        assertTrue(staging.contains("olt.gateway.writes.enabled=false"), staging)
        assertTrue(staging.contains("olt.gateway.snmp.trap.enabled=false"), staging)
        assertTrue(staging.contains("net.diag.enabled=false"), staging)
        assertTrue(staging.contains("net.diag.snmp.trap.udp-enabled=false"), staging)
        assertTrue(staging.contains("net.diag.syslog.udp-enabled=false"), staging)
        assertTrue(staging.contains("whatsapp.welcome-on-registration.enabled=false"), staging)
        assertTrue(staging.contains("whatsapp.retention.enabled=false"), staging)
        assertTrue(staging.contains("whatsapp.inbound-alert.enabled=false"), staging)
        assertTrue(staging.contains("traffic.internal-base-url=http://127.0.0.1:8080/ispadmin-staging-traffic"), staging)
        assertTrue(staging.contains("traffic.core-base-url=http://127.0.0.1:8080/ispadmin-staging"), staging)
        assertTrue(staging.contains("olt.gateway.internal-base-url=http://127.0.0.1:8080/ispadmin-staging-oltgateway"), staging)
        assertTrue(staging.contains("olt.gateway.client-enabled=false"), staging)
        assertTrue(staging.contains("gigafiber.redis.enabled=\${REDIS_ENABLED:true}"), staging)
        assertTrue(staging.contains("gigafiber.redis.host=\${REDIS_HOST:redis}"), staging)
    }

    @Test
    fun staging_war_bakes_prod_then_staging_profiles() {
        val root = Path.of(System.getProperty("user.dir"))
        val pom = Files.readString(root.resolve("pom.xml"))
        assertTrue(pom.contains("replace=\"spring.profiles.active=prod,staging,subsystem\""), pom)
        assertTrue(pom.contains("replace=\"spring.profiles.active=prod,traffic\""), pom)
        assertFalse(pom.contains("replace=\"spring.profiles.active=prod,staging,traffic\""), pom)
        assertTrue(pom.contains("jdbc:mysql://mysql:3306/stg_traffic?"), pom)
        assertTrue(pom.contains("jdbc:mysql://mysql:3306/prod_traffic?"), pom)
        assertTrue(pom.contains("replace=\"spring.profiles.active=prod,oltgateway\""), pom)
        assertTrue(pom.contains("jdbc:mysql://mysql:3306/stg_oltgateway?"), pom)
        assertTrue(pom.contains("jdbc:mysql://mysql:3306/prod_oltgateway?"), pom)
        assertTrue(pom.contains("replace=\"spring.profiles.active=prod,acs\""), pom)
        assertTrue(pom.contains("jdbc:mysql://mysql:3306/stg_acs?"), pom)
        assertTrue(pom.contains("jdbc:mysql://mysql:3306/prod_acs?"), pom)
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
            val text = Files.readString(root.resolve("src/main/resources/$name"))
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
        val traffic = Files.readString(root.resolve("src/main/resources/application-traffic.properties"))
        assertFalse(traffic.contains("spring.profiles.active="), traffic)
        assertTrue(traffic.contains("stg_traffic") || traffic.contains("prod_traffic"), traffic)
        assertTrue(traffic.contains("createDatabaseIfNotExist=true"), traffic)
        assertFalse(traffic.contains("spring.datasource.username="), traffic)
        assertFalse(traffic.contains("spring.datasource.password="), traffic)
    }

    @Test
    fun oltgateway_profile_file_does_not_set_active_profiles() {
        val root = Path.of(System.getProperty("user.dir"))
        val gateway = Files.readString(root.resolve("src/main/resources/application-oltgateway.properties"))
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
        val prod = Files.readString(root.resolve("src/main/resources/application-prod.properties"))
        assertTrue(prod.contains("gigafiber.redis.enabled=\${REDIS_ENABLED:false}"), prod)
        assertFalse(prod.contains("gigafiber.redis.enabled=false\n"), prod)
    }

    @Test
    fun staging_sibling_wars_bake_redis_on() {
        val root = Path.of(System.getProperty("user.dir"))
        val pom = Files.readString(root.resolve("pom.xml"))
        val trafficStaging = pom.substringAfter("<id>traffic-staging-war</id>").substringBefore("<id>oltgateway-war</id>")
        val gatewayStaging = pom.substringAfter("<id>oltgateway-staging-war</id>")
        assertTrue(trafficStaging.contains("gigafiber.redis.enabled=true"), trafficStaging)
        assertTrue(trafficStaging.contains("gigafiber.redis.host=redis"), trafficStaging)
        assertTrue(gatewayStaging.contains("gigafiber.redis.enabled=true"), gatewayStaging)
        assertTrue(gatewayStaging.contains("gigafiber.redis.host=redis"), gatewayStaging)
        assertTrue(
            gatewayStaging.contains("match=\"^gigafiber\\.redis\\.enabled=.*\""),
            gatewayStaging,
        )
        assertTrue(gatewayStaging.contains("olt.gateway.writes.enabled=true"), gatewayStaging)
    }

    @Test
    fun acs_staging_bakes_profiles_catalog() {
        val root = Path.of(System.getProperty("user.dir"))
        val pom = Files.readString(root.resolve("pom.xml"))
        val acsStaging = pom.substringAfter("<id>acs-staging-war</id>")
        assertTrue(acsStaging.contains("acs.profiles.catalog=ispadmin_staging"), acsStaging)
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
