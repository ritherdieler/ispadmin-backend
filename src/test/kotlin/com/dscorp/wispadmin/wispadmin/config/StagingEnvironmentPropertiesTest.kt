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
        assertTrue(staging.contains("olt.gateway.snmp.trap.enabled=false"), staging)
        assertTrue(staging.contains("net.diag.enabled=false"), staging)
        assertTrue(staging.contains("net.diag.snmp.trap.udp-enabled=false"), staging)
        assertTrue(staging.contains("net.diag.syslog.udp-enabled=false"), staging)
        assertTrue(staging.contains("whatsapp.welcome-on-registration.enabled=false"), staging)
        assertTrue(staging.contains("whatsapp.retention.enabled=false"), staging)
        assertTrue(staging.contains("whatsapp.inbound-alert.enabled=false"), staging)
    }

    @Test
    fun staging_war_bakes_prod_then_staging_profiles() {
        val root = Path.of(System.getProperty("user.dir"))
        val pom = Files.readString(root.resolve("pom.xml"))
        assertTrue(pom.contains("replace=\"spring.profiles.active=prod,staging\""), pom)
    }
}
