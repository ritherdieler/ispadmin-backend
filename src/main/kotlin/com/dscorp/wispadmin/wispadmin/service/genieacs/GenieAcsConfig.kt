package com.dscorp.wispadmin.wispadmin.service.genieacs

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestTemplate

@Configuration
class GenieAcsConfig {

    @Bean("genieAcsRestTemplate")
    fun genieAcsRestTemplate(properties: GenieAcsProperties): RestTemplate {
        val connect = properties.connectTimeoutMs.toInt().coerceAtLeast(1000)
        val read = (properties.taskTimeoutMs + 15_000).toInt().coerceAtLeast(5_000)
        val factory = SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(connect)
            setReadTimeout(read)
        }
        return RestTemplate(factory)
    }
}
