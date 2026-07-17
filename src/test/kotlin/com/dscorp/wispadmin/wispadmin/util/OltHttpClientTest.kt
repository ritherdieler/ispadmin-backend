package com.dscorp.wispadmin.wispadmin.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.http.client.ClientHttpRequestInterceptor
import org.springframework.test.web.client.ExpectedCount.once
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestTemplate

class OltHttpClientTest {

    @Test
    fun `get usa base url y api key configurados`() {
        val restTemplate = RestTemplate()
        val server = MockRestServiceServer.bindTo(restTemplate).build()
        val client = OltHttpClient(
            requestResponseLoggingInterceptor = noopInterceptor(),
            baseUrl = "https://olt.test/api/",
            apiKey = "secret-token",
            provider = "smartolt",
            authHeaderOverride = "",
            restTemplate = restTemplate
        )

        server.expect(once(), requestTo("https://olt.test/api/onu/unconfigured_onus"))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header("X-Token", "secret-token"))
            .andRespond(withSuccess("ok", MediaType.TEXT_PLAIN))

        val response = client.get("onu/unconfigured_onus", String::class.java)

        assertEquals("ok", response)
        server.verify()
    }

    @Test
    fun `post usa base url y api key configurados`() {
        val restTemplate = RestTemplate()
        val server = MockRestServiceServer.bindTo(restTemplate).build()
        val client = OltHttpClient(
            requestResponseLoggingInterceptor = noopInterceptor(),
            baseUrl = "https://olt.test/api/",
            apiKey = "secret-token",
            provider = "smartolt",
            authHeaderOverride = "",
            restTemplate = restTemplate
        )

        server.expect(once(), requestTo("https://olt.test/api/onu/reboot/1"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("X-Token", "secret-token"))
            .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON))

        val response = client.post("onu/reboot/1", null, String::class.java)

        assertEquals("{}", response)
        server.verify()
    }

    @Test
    fun `provider gateway usa header X-Olt-Gateway-Key`() {
        val restTemplate = RestTemplate()
        val server = MockRestServiceServer.bindTo(restTemplate).build()
        val client = OltHttpClient(
            requestResponseLoggingInterceptor = noopInterceptor(),
            baseUrl = "http://localhost:8080/ispadmin/api/olt-gateway/",
            apiKey = "dev-olt-gateway-key",
            provider = "gateway",
            authHeaderOverride = "",
            restTemplate = restTemplate
        )

        server.expect(once(), requestTo("http://localhost:8080/ispadmin/api/olt-gateway/onu/unconfigured_onus"))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header("X-Olt-Gateway-Key", "dev-olt-gateway-key"))
            .andRespond(withSuccess("{\"status\":true}", MediaType.APPLICATION_JSON))

        val response = client.get("onu/unconfigured_onus", String::class.java)

        assertEquals("{\"status\":true}", response)
        server.verify()
    }

    private fun noopInterceptor() = ClientHttpRequestInterceptor { request, body, execution ->
        execution.execute(request, body)
    }
}
