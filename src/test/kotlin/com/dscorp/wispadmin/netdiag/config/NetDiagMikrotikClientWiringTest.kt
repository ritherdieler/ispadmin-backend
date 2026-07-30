package com.dscorp.wispadmin.netdiag.config

import com.dscorp.wispadmin.routeros.adapter.LegrangeClassicAdapter
import com.dscorp.wispadmin.routeros.adapter.RouterOsRestClassicFallbackAdapter
import com.dscorp.wispadmin.routeros.config.RouterOsClientConfig
import com.dscorp.wispadmin.routeros.port.MikrotikClient
import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.runner.ApplicationContextRunner

class NetDiagMikrotikClientWiringTest {

    private val contextRunner = ApplicationContextRunner()
        .withUserConfiguration(NetDiagConfig::class.java, RouterOsClientConfig::class.java)
        .withBean(ObjectMapper::class.java)
        .withPropertyValues(
            "net.diag.enabled=true",
            "net.diag.api-key=test-netdiag-key",
            "router.os.client.adapter=classic"
        )

    @Test
    fun `netdiag uses REST with classic fallback when enabled`() {
        contextRunner.run { context ->
            val netDiagClient = context.getBean("netDiagMikrotikClient", MikrotikClient::class.java)
            assertThat(netDiagClient).isInstanceOf(RouterOsRestClassicFallbackAdapter::class.java)

            val wispadminClient = context.getBean(MikrotikClient::class.java)
            assertThat(wispadminClient).isNotSameAs(netDiagClient)
            assertThat(wispadminClient).isInstanceOf(LegrangeClassicAdapter::class.java)
        }
    }

    @Test
    fun `netdiag uses REST only when fallback disabled`() {
        contextRunner
            .withPropertyValues("net.diag.mikrotik.fallback-classic=false")
            .run { context ->
                val netDiagClient = context.getBean("netDiagMikrotikClient", MikrotikClient::class.java)
                assertThat(netDiagClient).isInstanceOf(com.dscorp.wispadmin.routeros.adapter.RouterOs7RestAdapter::class.java)
            }
    }

    @Test
    fun `netdiag still uses fallback when wispadmin adapter is rest`() {
        contextRunner
            .withPropertyValues("router.os.client.adapter=rest")
            .run { context ->
                val netDiagClient = context.getBean("netDiagMikrotikClient", MikrotikClient::class.java)
                assertThat(netDiagClient).isInstanceOf(RouterOsRestClassicFallbackAdapter::class.java)

                val wispadminClient = context.getBean(MikrotikClient::class.java)
                assertThat(wispadminClient).isInstanceOf(com.dscorp.wispadmin.routeros.adapter.RouterOs7RestAdapter::class.java)
                assertThat(wispadminClient).isNotSameAs(netDiagClient)
            }
    }
}
