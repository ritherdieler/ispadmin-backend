package com.dscorp.wispadmin.traffic.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestTemplate

@Configuration
class TrafficDirectoryHttpConfig {
    @Bean("trafficDirectoryRestTemplate")
    fun restTemplate()=RestTemplate(SimpleClientHttpRequestFactory().apply {
        setConnectTimeout(3_000)
        setReadTimeout(8_000)
    })
}
