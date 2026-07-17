package com.dscorp.wispadmin.wispadmin.config

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.SecurityScheme
import org.springdoc.core.GroupedOpenApi
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenApiConfig {

    companion object {
        const val OLT_GATEWAY_SECURITY_SCHEME = "OltGatewayApiKey"
    }

    @Bean
    fun wispAdminOpenApi(): OpenAPI {
        return OpenAPI()
            .info(
                Info()
                    .title("WispAdmin / ISP Admin API")
                    .description(
                        "API de ISP Admin (WispAdmin). Incluye el módulo OLT Gateway " +
                            "(lectura Huawei MA5608T). Los endpoints del gateway requieren " +
                            "el header X-Olt-Gateway-Key (excepto /health)."
                    )
                    .version("1.0")
            )
            .components(
                Components().addSecuritySchemes(
                    OLT_GATEWAY_SECURITY_SCHEME,
                    SecurityScheme()
                        .name("X-Olt-Gateway-Key")
                        .type(SecurityScheme.Type.APIKEY)
                        .`in`(SecurityScheme.In.HEADER)
                        .description("API key del OLT Gateway")
                )
            )
    }

    @Bean
    fun oltGatewayApi(): GroupedOpenApi {
        return GroupedOpenApi.builder()
            .group("olt-gateway")
            .displayName("OLT Gateway")
            .packagesToScan("com.dscorp.wispadmin.oltgateway")
            .pathsToMatch("/api/olt-gateway/**")
            .build()
    }
}
