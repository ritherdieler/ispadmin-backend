package com.dscorp.wispadmin.traffic.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain

@Configuration
@EnableWebSecurity
class TrafficSecurityConfig {
    @Bean
    @Order(3)
    fun trafficSecurityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http.requestMatchers()
            .antMatchers("/api/traffic/v1/**", "/traffic/**")
            .and()
            .csrf().disable()
            .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            .and()
            .authorizeRequests()
            .antMatchers("/actuator/health", "/actuator/health/**", "/api/traffic/v1/**", "/ws/**", "/traffic/**").permitAll()
            .anyRequest().denyAll()
        return http.build()
    }
}
