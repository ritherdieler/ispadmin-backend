package com.dscorp.wispadmin.wispadmin.oltclient

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestTemplate

class OltGatewayHttpClientTest {

    @Test
    fun `getJson llama gateway WAR con X-Olt-Gateway-Key`() {
        val restTemplate = RestTemplate()
        val server = MockRestServiceServer.createServer(restTemplate)
        server.expect(requestTo("http://127.0.0.1:8080/ispadmin-staging-oltgateway/api/olt-gateway/onus/configured?page=0&size=50"))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header(OltGatewayHttpClient.HEADER, "dev-olt-gateway-key"))
            .andRespond(withSuccess("""{"items":[],"page":0,"size":50,"totalElements":0,"totalPages":0}""", MediaType.APPLICATION_JSON))

        val client = OltGatewayHttpClient(
            OltGatewayClientProperties().apply {
                apiKey = "dev-olt-gateway-key"
                internalBaseUrl = "http://127.0.0.1:8080/ispadmin-staging-oltgateway"
            },
            restTemplate,
        )

        val body = client.getJson("/api/olt-gateway/onus/configured", "page=0&size=50").body
        assertEquals(true, body?.contains("\"totalElements\":0"))
        server.verify()
    }

    @Test
    fun `postForm llama gateway WAR con form-urlencoded y X-Olt-Gateway-Key`() {
        val restTemplate = RestTemplate()
        val server = MockRestServiceServer.createServer(restTemplate)
        server.expect(requestTo("http://127.0.0.1:8080/ispadmin-staging-oltgateway/api/olt-gateway/onu/authorize_onu"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header(OltGatewayHttpClient.HEADER, "dev-olt-gateway-key"))
            .andRespond(withSuccess("""{"status":true,"unique_external_id":"ext-1"}""", MediaType.APPLICATION_JSON))

        val client = OltGatewayHttpClient(
            OltGatewayClientProperties().apply {
                apiKey = "dev-olt-gateway-key"
                internalBaseUrl = "http://127.0.0.1:8080/ispadmin-staging-oltgateway"
            },
            restTemplate,
        )
        val form = org.springframework.util.LinkedMultiValueMap<String, String>()
        form.add("sn", "ZTEGDC47BFFD")

        val body = client.postForm("/api/olt-gateway/onu/authorize_onu", form).body
        assertEquals(true, body?.contains("\"unique_external_id\":\"ext-1\""))
        server.verify()
    }
}
