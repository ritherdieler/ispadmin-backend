package com.dscorp.wispadmin.acs.controller

import com.dscorp.wispadmin.acs.genieacs.GenieAcsClient
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.FilterType
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping

@SpringBootTest(classes = [OnboardingV2InternetMappingTest.Scan::class])
class OnboardingV2InternetMappingTest {
    @Autowired
    lateinit var context: ApplicationContext

    @Test
    fun internetRouteIsRegisteredWhenAcsDatasourceIsConfigured() {
        val internet = context.getBeansOfType(RequestMappingHandlerMapping::class.java).values.any { mappings ->
            mappings.handlerMethods.keys.any { info ->
                info.patternValues.any { it == "/api/acs/v1/onboarding-v2/internet" } &&
                    info.methodsCondition.methods.any { it.name == "POST" }
            }
        }
        assertTrue(internet)
    }

    @SpringBootApplication
    @ComponentScan(
        basePackages = ["com.dscorp.wispadmin.acs.controller", "com.dscorp.wispadmin.acs.config"],
        excludeFilters = [
            ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = [AcsCpeController::class, OnboardingV2AcsController::class]),
        ],
    )
    class Scan {
        @Bean
        fun genieAcsClient(): GenieAcsClient = mockk(relaxed = true)
    }
}
