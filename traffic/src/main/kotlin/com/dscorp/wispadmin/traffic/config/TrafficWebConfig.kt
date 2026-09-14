package com.dscorp.wispadmin.traffic.config

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class TrafficWebConfig {

    @Bean
    fun trafficApiKeyFilterRegistration(
        properties: TrafficProperties,
        objectMapper: ObjectMapper,
    ): FilterRegistrationBean<TrafficApiKeyFilter> {
        val registration = FilterRegistrationBean<TrafficApiKeyFilter>()
        registration.filter = TrafficApiKeyFilter(properties, objectMapper)
        registration.addUrlPatterns("/api/traffic/*")
        registration.order = 26
        return registration
    }
}
