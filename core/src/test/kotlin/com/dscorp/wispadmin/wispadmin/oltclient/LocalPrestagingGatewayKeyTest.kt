package com.dscorp.wispadmin.wispadmin.oltclient

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.core.env.PropertiesPropertySource
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestTemplate
import java.util.Properties

class LocalPrestagingGatewayKeyTest {

    @Test
    fun `uses staging-specific key when the general gateway key differs`() {
        val localProperties = Properties().apply {
            LocalPrestagingGatewayKeyTest::class.java
                .getResourceAsStream("/application-local-prestaging.properties")
                .use(::load)
        }
        val secretProperties = Properties().apply {
            setProperty("olt.gateway.api-key", "local-production-test-key")
        }
        val stagingEnvironment = Properties().apply {
            setProperty("OLT_GATEWAY_STAGING_API_KEY", "local-staging-test-key")
        }
        val runner = ApplicationContextRunner()
            .withUserConfiguration(OltGatewayClientPropertiesConfig::class.java)
            .withInitializer { context ->
                context.environment.propertySources.remove("systemEnvironment")
                context.environment.propertySources.remove("systemProperties")
                context.environment.propertySources.addFirst(
                    PropertiesPropertySource("staging-environment", stagingEnvironment),
                )
                context.environment.propertySources.addFirst(
                    PropertiesPropertySource("local-prestaging-secrets", secretProperties),
                )
                context.environment.propertySources.addLast(
                    PropertiesPropertySource("local-prestaging", localProperties),
                )
            }

        runner.run { context ->
            val restTemplate = RestTemplate()
            val server = MockRestServiceServer.createServer(restTemplate)
            server.expect(
                requestTo("http://127.0.0.1:8092/ispadmin/api/olt-gateway/onu/unconfigured_onus"),
            )
                .andExpect(header(OltGatewayHttpClient.HEADER, "local-staging-test-key"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON))

            OltGatewayHttpClient(
                context.getBean(OltGatewayClientProperties::class.java),
                restTemplate,
            ).getJson("/api/olt-gateway/onu/unconfigured_onus")

            server.verify()
        }
    }
}
