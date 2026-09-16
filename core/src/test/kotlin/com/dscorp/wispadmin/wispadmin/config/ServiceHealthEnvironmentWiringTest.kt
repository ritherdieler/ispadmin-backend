package com.dscorp.wispadmin.wispadmin.config

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class ServiceHealthEnvironmentWiringTest {

    private val root: Path = Path.of(System.getProperty("user.dir"))

    private fun overlay(name: String): String =
        Files.readString(root.resolve("core/src/main/resources/$name"))

    @Test
    fun prod_must_not_set_environment_tag_or_360_would_collect_every_id() {
        val prod = overlay("application-prod.properties")
        assertFalse(
            Regex("""^gigafiber\.environment\.tag=""", RegexOption.MULTILINE).containsMatchIn(prod),
            prod,
        )
        assertFalse(prod.contains("gigafiber.environment.tag=stg"), prod)
        assertFalse(prod.contains("gigafiber.environment.tag=lpstg"), prod)
    }

    @Test
    fun tagged_overlays_enable_360_traffic_wifi_and_stations() {
        val staging = overlay("application-staging.properties")
        val prestaging = overlay("application-local-prestaging.properties")
        assertTrue(staging.contains("gigafiber.environment.tag=stg"), staging)
        assertTrue(prestaging.contains("gigafiber.environment.tag=lpstg"), prestaging)
        listOf(staging, prestaging).forEach { text ->
            assertTrue(text.contains("service.health.enabled=true"), text)
            assertTrue(text.contains("service.health.optical-enabled=true"), text)
            assertTrue(text.contains("service.health.acs-enabled=true"), text)
        }
        assertTrue(staging.contains("gigafiber.subsystems.traffic.enabled=true"), staging)
        assertTrue(staging.contains("gigafiber.subsystems.servicehealth.enabled=true"), staging)
        assertTrue(staging.contains("traffic.poll.enabled=true"), staging)
        assertTrue(prestaging.contains("gigafiber.subsystems.servicehealth.enabled=true"), prestaging)
        assertTrue(prestaging.contains("gigafiber.subsystems.traffic.enabled=true"), prestaging)
    }
}
