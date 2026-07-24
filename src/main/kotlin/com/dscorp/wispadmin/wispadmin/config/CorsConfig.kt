package com.dscorp.wispadmin.wispadmin.config

import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource
import org.springframework.web.filter.CorsFilter

@Configuration
class CorsConfig {

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val source = UrlBasedCorsConfigurationSource()
        val config = CorsConfiguration()

        config.addAllowedOriginPattern("http://localhost:*")
        config.addAllowedOriginPattern("http://127.0.0.1:*")
        config.addAllowedOriginPattern("http://192.168.*.*:*")
        config.addAllowedOriginPattern("http://10.*.*.*:*")
        config.addAllowedOriginPattern("http://172.16.*.*:*")

        config.addAllowedOrigin("http://localhost:3000")
        config.addAllowedOrigin("http://localhost:5173")

        config.addAllowedOrigin("https://api.gigafiberperu.cloud")
        config.addAllowedOrigin("https://backoffice.gigafiberperu.cloud")
        config.addAllowedOrigin("https://asistencias.gigafiberperu.cloud")
        config.addAllowedOrigin("https://observability.gigafiberperu.cloud")

        config.addAllowedMethod("*")
        config.addAllowedHeader("*")
        config.allowCredentials = true
        config.maxAge = 3600L

        source.registerCorsConfiguration("/**", config)
        return source
    }

    @Bean
    fun corsFilter(corsConfigurationSource: CorsConfigurationSource): FilterRegistrationBean<CorsFilter> {
        val registration = FilterRegistrationBean(CorsFilter(corsConfigurationSource))
        registration.order = Ordered.HIGHEST_PRECEDENCE
        registration.setName("corsFilter")
        return registration
    }
}
