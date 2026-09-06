package com.dscorp.wispadmin.traffic.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain

@Configuration
@EnableWebSecurity
@Profile("traffic")
class TrafficSecurityConfig {
    @Bean
    fun trafficSecurityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http.csrf().disable()
            .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            .and()
            .authorizeRequests()
            .antMatchers("/actuator/health", "/actuator/health/**", "/api/traffic/v1/**", "/ws/**").permitAll()
            .anyRequest().denyAll()
        return http.build()
    }
}
