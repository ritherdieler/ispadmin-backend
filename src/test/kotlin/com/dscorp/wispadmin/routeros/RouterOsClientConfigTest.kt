package com.dscorp.wispadmin.routeros

import com.dscorp.wispadmin.routeros.adapter.RouterOs7RestAdapter
import com.dscorp.wispadmin.routeros.config.RouterOsClientConfig
import com.dscorp.wispadmin.routeros.config.RouterOsClientProperties
import com.dscorp.wispadmin.routeros.port.MikrotikClient
import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import java.nio.file.Path
import kotlin.io.path.readText

class RouterOsClientConfigTest {

    private val contextRunner = ApplicationContextRunner()
        .withUserConfiguration(RouterOsClientConfig::class.java)
        .withBean(ObjectMapper::class.java)

    @Test
    fun `warns when rest verify ssl is disabled`() {
        val properties = RouterOsClientProperties().apply {
            rest.verifySsl = false
        }
        val message = RouterOsClientConfig.sslVerifyDisabledWarning(properties)
        assertNotNull(message)
        assertTrue(message!!.contains("verify-ssl=false"))
    }

    @Test
    fun `no warning when rest verify ssl is enabled`() {
        val properties = RouterOsClientProperties().apply {
            rest.verifySsl = true
        }
        assertNull(RouterOsClientConfig.sslVerifyDisabledWarning(properties))
    }

    @Test
    fun `default adapter property is rest`() {
        assertEquals("rest", RouterOsClientProperties().adapter)
    }

    @Test
    fun `wires rest adapter as primary MikrotikClient`() {
        contextRunner.run { context ->
            assertThat(context).hasSingleBean(MikrotikClient::class.java)
            assertThat(context.getBean(MikrotikClient::class.java))
                .isInstanceOf(RouterOs7RestAdapter::class.java)
        }
    }

    @Test
    fun `pom does not declare mikrotik-java legrange dependency`() {
        val pom = Path.of(System.getProperty("user.dir")).resolve("pom.xml").readText()
        assertThat(pom).doesNotContain("GideonLeGrange")
        assertThat(pom).doesNotContain("mikrotik-java")
    }
}
