package com.dscorp.wispadmin.wispadmin.config

import com.dscorp.wispadmin.oltgateway.controller.OltGatewayController
import com.dscorp.wispadmin.oltgateway.exception.OltGatewayExceptionHandler
import com.dscorp.wispadmin.oltgateway.service.OltGatewayQueryFacade
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest(
    classes = [OpenApiDocsTest.OpenApiSliceApplication::class],
    webEnvironment = SpringBootTest.WebEnvironment.MOCK
)
@AutoConfigureMockMvc
@TestPropertySource(
    properties = [
        "olt.gateway.enabled=true",
        "springdoc.api-docs.path=/v3/api-docs",
        "springdoc.packages-to-scan=com.dscorp.wispadmin.oltgateway",
        "springdoc.paths-to-match=/api/olt-gateway/**",
        "spring.main.allow-bean-definition-overriding=true"
    ]
)
class OpenApiDocsTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun `v3 api-docs incluye paths del olt-gateway`() {
        mockMvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.info.title").value("WispAdmin / ISP Admin API"))
            .andExpect(jsonPath("$.paths['/api/olt-gateway/health']").exists())
            .andExpect(jsonPath("$.paths['/api/olt-gateway/olt/info']").exists())
            .andExpect(jsonPath("$.paths['/api/olt-gateway/onus/autofind']").exists())
            .andExpect(jsonPath("$.paths['/api/olt-gateway/onus/by-sn/{sn}']").exists())
            .andExpect(jsonPath("$.paths['/api/olt-gateway/onus']").exists())
            .andExpect(jsonPath("$.paths['/api/olt-gateway/onus/{slot}/{port}/{ontId}']").exists())
            .andExpect(jsonPath("$.paths['/api/olt-gateway/onus/{slot}/{port}/{ontId}/optical']").exists())
            .andExpect(jsonPath("$.components.securitySchemes.OltGatewayApiKey.name").value("X-Olt-Gateway-Key"))
    }

    @Test
    fun `grupo olt-gateway expone api-docs filtrado`() {
        mockMvc.perform(get("/v3/api-docs/olt-gateway"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.paths['/api/olt-gateway/health']").exists())
    }

    @SpringBootApplication(
        scanBasePackages = ["org.springdoc"],
        exclude = [DataSourceAutoConfiguration::class, HibernateJpaAutoConfiguration::class]
    )
    @Import(OpenApiConfig::class, OltGatewayController::class, OltGatewayExceptionHandler::class)
    class OpenApiSliceApplication {
        @Bean
        @Primary
        fun queryFacade(): OltGatewayQueryFacade = mockk(relaxed = true)
    }
}
