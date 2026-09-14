package com.dscorp.wispadmin.wispadmin.oltclient

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestTemplate
import com.dscorp.wispadmin.transport.RegistrationTimingClientInterceptor

@Configuration
class OltGatewayClientConfig {

    companion object {
        const val CONNECT_TIMEOUT_MS = 5_000
        const val READ_TIMEOUT_MS = 180_000
    }

    @Bean("oltGatewayRestTemplate")
    fun oltGatewayRestTemplate(
        timingInterceptor: ObjectProvider<RegistrationTimingClientInterceptor>? = null,
    ): RestTemplate {
        val factory = SimpleClientHttpRequestFactory()
        factory.setConnectTimeout(CONNECT_TIMEOUT_MS)
        factory.setReadTimeout(READ_TIMEOUT_MS)
        return RestTemplate(factory).apply {
            timingInterceptor?.ifAvailable?.let { interceptors.add(it) }
        }
    }

    @Bean
    @ConditionalOnProperty(prefix = "olt.gateway", name = ["client-enabled"], havingValue = "true")
    fun oltGatewayHttpClient(
        properties: OltGatewayClientProperties,
        @Qualifier("oltGatewayRestTemplate") restTemplate: RestTemplate,
    ): OltGatewayHttpClient = OltGatewayHttpClient(properties, restTemplate)

    @Bean
    @ConditionalOnProperty(prefix = "olt.gateway", name = ["client-enabled"], havingValue = "true")
    fun gatewayOnuActivationClient(
        http: OltGatewayHttpClient,
        objectMapper: ObjectMapper,
    ): GatewayOnuActivationClient = GatewayOnuActivationClient(http, objectMapper)
}
