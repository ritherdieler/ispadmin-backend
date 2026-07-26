package com.dscorp.wispadmin.routeros

import com.dscorp.wispadmin.routeros.adapter.LegrangeClassicAdapter
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

class RouterOsClientConfigTest {

    private val contextRunner = ApplicationContextRunner()
        .withUserConfiguration(RouterOsClientConfig::class.java)
        .withBean(ObjectMapper::class.java)

    @Test
    fun `warns when rest verify ssl is disabled`() {
        val properties = RouterOsClientProperties().apply {
            adapter = "rest"
            rest.verifySsl = false
        }
        val message = RouterOsClientConfig.sslVerifyDisabledWarning(properties)
        assertNotNull(message)
        assertTrue(message!!.contains("verify-ssl=false"))
    }

    @Test
    fun `no warning when rest verify ssl is enabled`() {
        val properties = RouterOsClientProperties().apply {
            adapter = "rest"
            rest.verifySsl = true
        }
        assertNull(RouterOsClientConfig.sslVerifyDisabledWarning(properties))
    }

    @Test
    fun `default adapter property is classic`() {
        assertEquals("classic", RouterOsClientProperties().adapter)
    }

    @Test
    fun `wires classic adapter when property is missing`() {
        contextRunner.run { context ->
            assertThat(context).hasSingleBean(MikrotikClient::class.java)
            assertThat(context.getBean(MikrotikClient::class.java))
                .isInstanceOf(LegrangeClassicAdapter::class.java)
        }
    }

    @Test
    fun `wires classic adapter when property is classic`() {
        contextRunner
            .withPropertyValues("router.os.client.adapter=classic")
            .run { context ->
                assertThat(context).hasSingleBean(MikrotikClient::class.java)
                assertThat(context.getBean(MikrotikClient::class.java))
                    .isInstanceOf(LegrangeClassicAdapter::class.java)
            }
    }

    @Test
    fun `wires rest adapter when property is rest`() {
        contextRunner
            .withPropertyValues("router.os.client.adapter=rest")
            .run { context ->
                assertThat(context).hasSingleBean(MikrotikClient::class.java)
                assertThat(context.getBean(MikrotikClient::class.java))
                    .isInstanceOf(RouterOs7RestAdapter::class.java)
            }
    }
}
