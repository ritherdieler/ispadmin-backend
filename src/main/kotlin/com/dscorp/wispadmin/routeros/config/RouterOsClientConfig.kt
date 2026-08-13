package com.dscorp.wispadmin.routeros.config

import com.dscorp.wispadmin.routeros.adapter.RouterOs7RestAdapter
import com.dscorp.wispadmin.routeros.port.MikrotikClient
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import javax.annotation.PostConstruct

@Configuration
@EnableConfigurationProperties(RouterOsClientProperties::class)
class RouterOsClientConfig(
    private val properties: RouterOsClientProperties
) {

    private val logger = LoggerFactory.getLogger(RouterOsClientConfig::class.java)

    @PostConstruct
    fun warnIfSslVerificationDisabled() {
        sslVerifyDisabledWarning(properties)?.let { warning ->
            logger.warn(warning)
        }
    }

    @Bean(destroyMethod = "close")
    @Primary
    fun routerOs7RestAdapter(
        objectMapper: ObjectMapper,
        @Value("\${mikrotik.connection.mock.enabled:false}") mockEnabled: Boolean
    ): MikrotikClient {
        return RouterOs7RestAdapter(
            properties = properties,
            objectMapper = objectMapper,
            mockEnabled = mockEnabled
        )
    }

    companion object {
        fun sslVerifyDisabledWarning(properties: RouterOsClientProperties): String? {
            if (!properties.rest.verifySsl) {
                return "router.os.client.rest.verify-ssl=false: TLS certificate verification is disabled; use only in local lab profiles"
            }
            return null
        }
    }
}
