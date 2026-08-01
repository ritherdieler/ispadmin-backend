package com.dscorp.wispadmin.netdiag.config

import com.dscorp.wispadmin.oltgateway.config.OltGatewayProperties
import com.dscorp.wispadmin.oltgateway.parser.HuaweiOltAlarmParser
import com.dscorp.wispadmin.routeros.adapter.LegrangeClassicAdapter
import com.dscorp.wispadmin.routeros.adapter.RouterOs7RestAdapter
import com.dscorp.wispadmin.routeros.adapter.RouterOsRestClassicFallbackAdapter
import com.dscorp.wispadmin.routeros.config.RouterOsClientProperties
import com.dscorp.wispadmin.routeros.port.MikrotikClient
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestTemplate

@Configuration
@EnableConfigurationProperties(NetDiagProperties::class, OltGatewayProperties::class)
@ConditionalOnProperty(prefix = "net.diag", name = ["enabled"], havingValue = "true")
class NetDiagConfig {

    @Bean
    fun huaweiOltAlarmParser(): HuaweiOltAlarmParser = HuaweiOltAlarmParser()

    @Bean
    fun netDiagApiKeyFilterRegistration(
        properties: NetDiagProperties,
        objectMapper: ObjectMapper
    ): FilterRegistrationBean<NetDiagApiKeyFilter> {
        val registration = FilterRegistrationBean<NetDiagApiKeyFilter>()
        registration.filter = NetDiagApiKeyFilter(properties, objectMapper)
        registration.addUrlPatterns("/api/netdiag/*")
        registration.order = 25
        return registration
    }

    @Bean(name = ["netDiagMikrotikClient"], destroyMethod = "close")
    fun netDiagMikrotikClient(
        routerOsClientProperties: RouterOsClientProperties,
        netDiagProperties: NetDiagProperties,
        objectMapper: ObjectMapper
    ): MikrotikClient {
        val restClient = RouterOs7RestAdapter(routerOsClientProperties, objectMapper)
        if (!netDiagProperties.mikrotik.fallbackClassic) {
            return restClient
        }
        @Suppress("DEPRECATION")
        val classicClient = LegrangeClassicAdapter(routerOsClientProperties)
        return RouterOsRestClassicFallbackAdapter(restClient, classicClient)
    }

    @Bean(name = ["netDiagRestTemplate"])
    fun netDiagRestTemplate(properties: NetDiagProperties): RestTemplate {
        val factory = SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(properties.llm.webhookTimeoutMs.toInt())
            setReadTimeout(properties.llm.webhookTimeoutMs.toInt())
        }
        return RestTemplate(factory)
    }
}
