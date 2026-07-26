package com.dscorp.wispadmin.netdiag.config

import com.dscorp.wispadmin.routeros.adapter.RouterOs7RestAdapter
import com.dscorp.wispadmin.routeros.config.RouterOsClientProperties
import com.dscorp.wispadmin.routeros.port.MikrotikClient
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(NetDiagProperties::class)
@ConditionalOnProperty(prefix = "net.diag", name = ["enabled"], havingValue = "true")
class NetDiagConfig {

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
        objectMapper: ObjectMapper
    ): MikrotikClient {
        return RouterOs7RestAdapter(routerOsClientProperties, objectMapper)
    }
}
