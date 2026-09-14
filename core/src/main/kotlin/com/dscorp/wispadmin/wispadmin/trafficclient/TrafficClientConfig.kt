package com.dscorp.wispadmin.wispadmin.trafficclient

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestTemplate

@Configuration
class TrafficClientConfig {

    @Bean
    fun coreTrafficApiKeyFilterRegistration(
        properties: TrafficClientProperties,
        objectMapper: ObjectMapper,
    ): FilterRegistrationBean<CoreTrafficApiKeyFilter> {
        val registration = FilterRegistrationBean<CoreTrafficApiKeyFilter>()
        registration.filter = CoreTrafficApiKeyFilter(properties, objectMapper)
        registration.addUrlPatterns("/internal/traffic/*")
        registration.order = 26
        return registration
    }

    @Bean("trafficRestTemplate")
    fun trafficRestTemplate(): RestTemplate {
        val factory = SimpleClientHttpRequestFactory()
        factory.setConnectTimeout(3_000)
        factory.setReadTimeout(8_000)
        return RestTemplate(factory)
    }

    @Bean
    fun trafficHttpClient(
        properties: TrafficClientProperties,
        @Qualifier("trafficRestTemplate") restTemplate: RestTemplate,
    ): TrafficHttpClient = TrafficHttpClient(properties, restTemplate)
}
