package com.dscorp.wispadmin.routeros.config

import com.dscorp.wispadmin.routeros.adapter.LegrangeClassicAdapter
import com.dscorp.wispadmin.routeros.adapter.RouterOs7RestAdapter
import com.dscorp.wispadmin.routeros.port.MikrotikClient
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
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
    @ConditionalOnProperty(
        prefix = "router.os.client",
        name = ["adapter"],
        havingValue = "classic",
        matchIfMissing = true
    )
    fun legrangeClassicAdapter(): MikrotikClient {
        return LegrangeClassicAdapter(properties)
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(
        prefix = "router.os.client",
        name = ["adapter"],
        havingValue = "rest"
    )
    fun routerOs7RestAdapter(objectMapper: ObjectMapper): MikrotikClient {
        return RouterOs7RestAdapter(properties, objectMapper)
    }

    companion object {
        fun sslVerifyDisabledWarning(properties: RouterOsClientProperties): String? {
            if (properties.adapter.equals("rest", ignoreCase = true) && !properties.rest.verifySsl) {
                return "router.os.client.rest.verify-ssl=false: TLS certificate verification is disabled; use only in local lab profiles"
            }
            return null
        }
    }
}
