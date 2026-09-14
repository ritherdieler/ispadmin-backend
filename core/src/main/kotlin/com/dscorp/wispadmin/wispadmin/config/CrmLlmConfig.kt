package com.dscorp.wispadmin.wispadmin.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestTemplate

@Configuration
class CrmLlmConfig {

    @Bean("crmLlmRestTemplate")
    fun crmLlmRestTemplate(properties: CrmLlmProperties): RestTemplate {
        val factory = SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(properties.timeoutMs.toInt().coerceAtLeast(1000))
            setReadTimeout(properties.timeoutMs.toInt().coerceAtLeast(1000))
        }
        return RestTemplate(factory)
    }
}
