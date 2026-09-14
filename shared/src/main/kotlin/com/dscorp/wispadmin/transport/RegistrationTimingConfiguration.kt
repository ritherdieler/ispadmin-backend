package com.dscorp.wispadmin.transport

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment

@Configuration
class RegistrationTimingConfiguration(
    private val environment: Environment,
) {
    @Bean
    fun registrationTiming(): RegistrationTiming {
        return RegistrationTiming(
            enabled = RegistrationTimingSupport.enabled(environment),
            war = RegistrationTimingSupport.warName(environment.getProperty("server.servlet.context-path")),
        )
    }

    @Bean
    fun registrationTimingFilter(timing: RegistrationTiming): RegistrationTimingFilter =
        RegistrationTimingFilter(timing)

    @Bean
    fun registrationTimingClientInterceptor(timing: RegistrationTiming): RegistrationTimingClientInterceptor =
        RegistrationTimingClientInterceptor(timing)
}
