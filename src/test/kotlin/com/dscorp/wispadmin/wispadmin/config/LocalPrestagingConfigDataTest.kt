package com.dscorp.wispadmin.wispadmin.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor
import org.springframework.core.env.StandardEnvironment

class LocalPrestagingConfigDataTest {

    private fun load(vararg profiles: String): StandardEnvironment {
        val environment = StandardEnvironment()
        environment.setActiveProfiles(*profiles)
        ConfigDataEnvironmentPostProcessor.applyTo(environment)
        return environment
    }

    @Test
    fun gateway_prestaging_without_prod_uses_lan_jdbc_and_host() {
        val environment = load("oltgateway", "local-prestaging")
        val url = environment.getProperty("spring.datasource.url").orEmpty()
        assertTrue(url.contains("localhost"), url)
        assertTrue(url.contains("prestaging_oltgateway"), url)
        assertFalse(url.contains("mysql:3306"), url)
        assertEquals("10.11.104.2", environment.getProperty("olt.gateway.host"))
        assertEquals("GATEWAY", environment.getProperty("olt.provider.authorize"))
        assertEquals("classpath:db/oltgateway", environment.getProperty("spring.flyway.locations"))
        assertEquals("0", environment.getProperty("spring.flyway.baseline-version"))
        assertEquals(
            "http://127.0.0.1:8090/ispadmin-acs",
            environment.getProperty("olt.gateway.acs.internal-base-url"),
        )
    }

    @Test
    fun acs_prestaging_uses_local_schema_and_nbi_tunnel() {
        val environment = load("acs", "local-prestaging")
        val url = environment.getProperty("spring.datasource.url").orEmpty()
        assertTrue(url.contains("localhost"), url)
        assertTrue(url.contains("prestaging_acs"), url)
        assertFalse(url.contains("prod_acs"), url)
        assertEquals("http://127.0.0.1:7557", environment.getProperty("genieacs.nbi-base-url"))
        assertEquals("8090", environment.getProperty("server.port"))
        assertEquals("classpath:db/acs", environment.getProperty("spring.flyway.locations"))
        assertEquals("0", environment.getProperty("spring.flyway.baseline-version"))
        assertEquals("false", environment.getProperty("spring.flyway.enabled"))
    }

    @Test
    fun core_prestaging_uses_own_schema() {
        val environment = load("local-prestaging")
        val url = environment.getProperty("spring.datasource.url").orEmpty()
        assertTrue(url.contains("localhost"), url)
        assertTrue(url.contains("ispadmin_prestaging"), url)
        assertEquals("8082", environment.getProperty("server.port"))
        assertEquals("lpstg", environment.getProperty("gigafiber.environment.tag"))
        assertEquals("false", environment.getProperty("spring.flyway.enabled"))
    }
}
