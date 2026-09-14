package com.dscorp.wispadmin.wispadmin.acsclient

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestTemplate

@Configuration
class AcsClientConfig {

    @Bean("acsRestTemplate")
    fun acsRestTemplate(): RestTemplate {
        val factory = SimpleClientHttpRequestFactory()
        factory.setConnectTimeout(3_000)
        factory.setReadTimeout(60_000)
        return RestTemplate(factory)
    }

    @Bean
    @ConditionalOnProperty(prefix = "acs", name = ["client-enabled"], havingValue = "true")
    fun acsHttpClient(
        properties: AcsClientProperties,
        @Qualifier("acsRestTemplate") restTemplate: RestTemplate,
    ): AcsHttpClient = AcsHttpClient(properties, restTemplate)

    @Bean
    @ConditionalOnProperty(prefix = "acs", name = ["client-enabled"], havingValue = "true")
    fun acsCpeCoreClient(
        http: AcsHttpClient,
        objectMapper: ObjectMapper,
    ): AcsCpeCoreClient = AcsCpeCoreClient(http, objectMapper)
}
