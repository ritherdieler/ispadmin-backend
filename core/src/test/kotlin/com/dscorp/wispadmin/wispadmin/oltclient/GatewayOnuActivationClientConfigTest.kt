package com.dscorp.wispadmin.wispadmin.oltclient

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

class GatewayOnuActivationClientConfigTest {

    private val runner = ApplicationContextRunner()
        .withUserConfiguration(
            OltGatewayClientPropertiesConfig::class.java,
            OltGatewayClientConfig::class.java,
            ObjectMapperConfig::class.java,
        )

    @Test
    fun `registers activation client when client-enabled is true`() {
        runner
            .withPropertyValues(
                "olt.gateway.client-enabled=true",
                "olt.gateway.internal-base-url=http://127.0.0.1:8080/ispadmin-staging-oltgateway",
                "olt.gateway.api-key=test-key",
            )
            .run { context ->
                assertNotNull(context.getBean(OltGatewayHttpClient::class.java))
                assertNotNull(context.getBean(GatewayOnuActivationClient::class.java))
            }
    }

    @Test
    fun `skips activation client when client-enabled is false`() {
        runner
            .withPropertyValues(
                "olt.gateway.client-enabled=false",
                "olt.gateway.internal-base-url=http://127.0.0.1:8080/ispadmin-staging-oltgateway",
            )
            .run { context ->
                assertNull(context.getBeanProvider(GatewayOnuActivationClient::class.java).ifAvailable)
                assertNull(context.getBeanProvider(OltGatewayHttpClient::class.java).ifAvailable)
            }
    }

    @Configuration
    class ObjectMapperConfig {
        @Bean
        fun objectMapper(): ObjectMapper = ObjectMapper()
    }
}
