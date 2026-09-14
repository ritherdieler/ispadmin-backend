package com.dscorp.wispadmin.wispadmin.config

import com.dscorp.wispadmin.wispadmin.tracing.TracingInterceptorHolder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestTemplate

@Configuration
class SmartOltHttpConfig {

    @Bean("smartOltRestTemplate")
    fun smartOltRestTemplate(properties: OltServiceProperties): RestTemplate {
        val factory = SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(properties.connectTimeoutMs.toInt().coerceAtLeast(1_000))
            setReadTimeout(properties.readTimeoutMs.toInt().coerceAtLeast(1_000))
        }
        return RestTemplate(factory).apply {
            TracingInterceptorHolder.instance?.let { interceptors.add(it) }
        }
    }
}
