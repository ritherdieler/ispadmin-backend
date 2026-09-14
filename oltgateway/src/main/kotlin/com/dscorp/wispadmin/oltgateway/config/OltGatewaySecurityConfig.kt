package com.dscorp.wispadmin.oltgateway.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain

@Configuration
@EnableWebSecurity
class OltGatewaySecurityConfig {
    @Bean
    @Order(2)
    fun oltGatewaySecurityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http.antMatcher("/api/olt-gateway/**")
            .csrf().disable()
            .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            .and()
            .authorizeRequests().anyRequest().permitAll()
        return http.build()
    }
}
