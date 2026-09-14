package com.dscorp.wispadmin.wispadmin.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties

class LocalPrestagingConfigDataTest {

    private val root = Path.of(System.getProperty("user.dir"))

    private fun prestaging(): Properties {
        val properties = Properties()
        Files.newInputStream(root.resolve("core/src/main/resources/application-local-prestaging.properties")).use {
            properties.load(it)
        }
        return properties
    }

    @Test
    fun prestaging_uses_lan_jdbc_and_loopback_http() {
        val properties = prestaging()
        val url = properties.getProperty("spring.datasource.url").orEmpty()
        assertTrue(url.contains("localhost"), url)
        assertTrue(url.contains("ispadmin_prestaging"), url)
        assertFalse(url.contains("mysql:3306"), url)
        assertEquals("10.11.104.2", properties.getProperty("olt.gateway.host"))
        assertEquals("GATEWAY", properties.getProperty("olt.provider.authorize"))
        assertEquals("http://127.0.0.1:8082/ispadmin", properties.getProperty("olt.gateway.internal-base-url"))
        assertEquals("http://127.0.0.1:8082/ispadmin", properties.getProperty("olt.gateway.acs.internal-base-url"))
        assertEquals("http://127.0.0.1:8082/ispadmin", properties.getProperty("acs.internal-base-url"))
        assertTrue(properties.getProperty("acs.datasource.url").orEmpty().contains("prestaging_acs"))
        assertTrue(properties.getProperty("oltgateway.datasource.url").orEmpty().contains("prestaging_oltgateway"))
        assertEquals("\${spring.datasource.username}", properties.getProperty("acs.datasource.username"))
        assertEquals("\${spring.datasource.password}", properties.getProperty("acs.datasource.password"))
        assertEquals("\${spring.datasource.username}", properties.getProperty("oltgateway.datasource.username"))
        assertEquals("\${spring.datasource.password}", properties.getProperty("oltgateway.datasource.password"))
        assertEquals("\${spring.datasource.username}", properties.getProperty("traffic.datasource.username"))
        assertEquals("\${spring.datasource.password}", properties.getProperty("traffic.datasource.password"))
        assertEquals("8082", properties.getProperty("server.port"))
        assertEquals("lpstg", properties.getProperty("gigafiber.environment.tag"))
        assertEquals("false", properties.getProperty("spring.flyway.enabled"))
        assertEquals("http://127.0.0.1:7557", properties.getProperty("genieacs.nbi-base-url"))
    }

    @Test
    fun prestaging_uses_real_mk2_not_mikrotik_mock() {
        val properties = prestaging()
        assertEquals("false", properties.getProperty("mikrotik.connection.mock.enabled"))
        assertEquals("false", properties.getProperty("olt.service.mock.enabled"))
        val overrideIp = properties.getProperty("mikrotik.connection.override.ip").orEmpty()
        assertTrue(overrideIp.isEmpty(), overrideIp)
    }
}
