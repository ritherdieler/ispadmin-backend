package com.dscorp.wispadmin.oltgateway.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class OltGatewaySnmpTimeoutPropertiesTest {

    @Test
    fun `kotlin defaults timeout 15s retries 1`() {
        val snmp = OltGatewayProperties().snmp
        assertEquals(15_000L, snmp.timeoutMs)
        assertEquals(1, snmp.retries)
        assertEquals(1_200_000L, snmp.pollLockTtlMs)
        assertEquals("olt-snmp-poll", snmp.pollLockKey)
        assertTrue(snmp.pollLockEnabled)
        assertTrue(snmp.pollLockShared)
        assertEquals(3, snmp.opticalParallelPorts)
        assertFalse(snmp.opticalPerPortWalks)
    }

    @Test
    fun `prod and dev bake timeout 15s retries 1 and shared poll lock`() {
        val root = Path.of(System.getProperty("user.dir"))
        listOf("application-prod.properties", "application-dev.properties").forEach { name ->
            val text = Files.readString(root.resolve("src/main/resources/$name"))
            assertTrue(
                text.contains("olt.gateway.snmp.timeout-ms=\${OLT_GATEWAY_SNMP_TIMEOUT_MS:15000}"),
                "$name timeout: $text",
            )
            assertTrue(
                text.contains("olt.gateway.snmp.retries=\${OLT_GATEWAY_SNMP_RETRIES:1}"),
                "$name retries: $text",
            )
            assertTrue(
                text.contains("olt.gateway.snmp.optical-parallel-ports=\${OLT_GATEWAY_SNMP_OPTICAL_PARALLEL_PORTS:3}"),
                "$name parallel ports: $text",
            )
            assertTrue(
                text.contains("olt.gateway.snmp.optical-per-port-walks=\${OLT_GATEWAY_SNMP_OPTICAL_PER_PORT:false}"),
                "$name per-port: $text",
            )
            assertTrue(
                text.contains("olt.gateway.snmp.poll-lock-enabled=\${OLT_GATEWAY_SNMP_POLL_LOCK_ENABLED:true}"),
                "$name poll lock: $text",
            )
            assertTrue(
                text.contains("olt.gateway.snmp.poll-lock-key=\${OLT_GATEWAY_SNMP_POLL_LOCK_KEY:olt-snmp-poll}"),
                "$name poll lock key: $text",
            )
            assertTrue(
                text.contains("olt.gateway.snmp.poll-lock-ttl-ms=\${OLT_GATEWAY_SNMP_POLL_LOCK_TTL_MS:1200000}"),
                "$name poll lock ttl: $text",
            )
            assertTrue(
                text.contains("olt.gateway.snmp.poll-lock-shared=\${OLT_GATEWAY_SNMP_POLL_LOCK_SHARED:true}"),
                "$name poll lock shared: $text",
            )
        }
    }

    @Test
    fun `poll lock env names are catalogued without values`() {
        val root = Path.of(System.getProperty("user.dir"))
        val secrets = Files.readString(root.resolve(".agent-docs/vps-secrets-management.md"))
        val example = Files.readString(root.resolve("scripts/deploy.config.example"))
        listOf(
            "OLT_GATEWAY_SNMP_TIMEOUT_MS",
            "OLT_GATEWAY_SNMP_POLL_LOCK_ENABLED",
            "OLT_GATEWAY_SNMP_POLL_LOCK_KEY",
            "OLT_GATEWAY_SNMP_POLL_LOCK_TTL_MS",
            "OLT_GATEWAY_SNMP_POLL_LOCK_SHARED",
        ).forEach { name ->
            assertTrue(secrets.contains(name), "secrets missing $name")
            assertTrue(example.contains(name), "example missing $name")
            assertFalse(Regex("$name=\\S+").containsMatchIn(example), example)
        }
    }

    @Test
    fun `staging gateway overlay does not pin parallel 1 or per-port true`() {
        val pom = Files.readString(Path.of(System.getProperty("user.dir")).resolve("pom.xml"))
        val staging = pom.substringAfter("<id>oltgateway-staging-war</id>").substringBefore("<id>acs-war</id>")
        assertFalse(staging.contains("optical-parallel-ports=1"), staging)
        assertFalse(staging.contains("optical-per-port-walks=true"), staging)
        assertFalse(staging.contains("OLT_GATEWAY_SNMP_OPTICAL_PARALLEL_PORTS:1"), staging)
    }
}
