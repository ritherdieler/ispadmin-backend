package com.dscorp.wispadmin.acs.config

import com.dscorp.wispadmin.acs.service.OnboardingV2TaskService
import com.dscorp.wispadmin.acs.service.OnboardingV2BaselineCipher
import com.dscorp.wispadmin.acs.genieacs.GenieAcsClient
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.core.JdbcTemplate
import javax.sql.DataSource

@Configuration
@ConditionalOnProperty(prefix = "gigafiber.subsystems.acs", name = ["enabled"], havingValue = "true", matchIfMissing = true)
@ConditionalOnExpression("'\${acs.datasource.url:}'.trim().length() > 0")
class OnboardingV2TaskConfig {
    @Bean
    fun onboardingV2BaselineCipher(properties: AcsProperties) = OnboardingV2BaselineCipher(properties)

    @Bean
    fun onboardingV2TaskService(
        @Qualifier("acsDataSource") source: DataSource,
        client: GenieAcsClient,
        json: ObjectMapper,
        baselineCipher: OnboardingV2BaselineCipher,
    ) = OnboardingV2TaskService(JdbcTemplate(source), client, json, baselineCipher)
}
