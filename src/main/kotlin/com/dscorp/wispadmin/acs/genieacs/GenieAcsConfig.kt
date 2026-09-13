package com.dscorp.wispadmin.acs.genieacs

import org.springframework.beans.factory.ObjectProvider
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestTemplate
import com.dscorp.wispadmin.transport.RegistrationTimingClientInterceptor

@Configuration
class GenieAcsConfig {

    @Bean("genieAcsRestTemplate")
    fun genieAcsRestTemplate(
        properties: GenieAcsProperties,
        timingInterceptor: ObjectProvider<RegistrationTimingClientInterceptor>,
    ): RestTemplate {
        val connect = properties.connectTimeoutMs.toInt().coerceAtLeast(1000)
        val read = (properties.taskTimeoutMs + 15_000).toInt().coerceAtLeast(5_000)
        val factory = SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(connect)
            setReadTimeout(read)
        }
        return RestTemplate(factory).apply {
            timingInterceptor.ifAvailable?.let { interceptors.add(it) }
        }
    }

    @Bean("acsGatewayRestTemplate")
    fun acsGatewayRestTemplate(): RestTemplate {
        val factory = SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(2_000)
            setReadTimeout(3_000)
        }
        return RestTemplate(factory)
    }
}
