package com.dscorp.wispadmin.acs.config

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain

@Configuration
@EnableConfigurationProperties(AcsProperties::class)
class AcsConfig {
    @Bean
    fun acsApiKeyFilterRegistration(
        properties: AcsProperties,
        objectMapper: ObjectMapper,
    ): FilterRegistrationBean<AcsApiKeyFilter> {
        val registration = FilterRegistrationBean<AcsApiKeyFilter>()
        registration.filter = AcsApiKeyFilter(properties, objectMapper)
        registration.addUrlPatterns("/api/acs/v1/*")
        registration.order = 25
        return registration
    }
}

@Configuration
@EnableWebSecurity
@Profile("acs")
class AcsSecurityConfig {
    @Bean
    fun acsSecurityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http.csrf().disable()
            .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            .and()
            .authorizeRequests().anyRequest().permitAll()
        return http.build()
    }
}
