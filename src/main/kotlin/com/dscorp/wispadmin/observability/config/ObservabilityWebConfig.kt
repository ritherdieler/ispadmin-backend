package com.dscorp.wispadmin.observability.config

import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class ObservabilityWebConfig(
    private val obsMetricInterceptor: ObsMetricInterceptor
) : WebMvcConfigurer {

    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(obsMetricInterceptor)
    }
}
