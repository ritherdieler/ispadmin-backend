package com.dscorp.wispadmin.wispadmin.oltclient

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.match.MockRestRequestMatchers.content
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestTemplate

class OltGatewayHttpClientTest {

    @Test fun `OMCI management sends exact target through authenticated gateway contract`() {
        val restTemplate = RestTemplate()
        val server = MockRestServiceServer.createServer(restTemplate)
        server.expect(requestTo("http://gateway/api/olt-gateway/onus/HWTC9F4BF950/omci/management"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header(OltGatewayHttpClient.HEADER, "key"))
            .andExpect(content().json("""{"sn":"HWTC9F4BF950","slot":1,"port":6,"ontId":116,"tr069ProfileId":7}"""))
            .andRespond(withSuccess("""{"configured":true,"address":"10.0.0.5"}""", MediaType.APPLICATION_JSON))
        val client = GatewayOnuActivationClient(OltGatewayHttpClient(OltGatewayClientProperties().apply {
            internalBaseUrl = "http://gateway"; apiKey = "key"
        }, restTemplate), com.fasterxml.jackson.module.kotlin.jacksonObjectMapper())

        val result = client.ensureOmciManagement(GatewayOmciManagementRequest("HWTC9F4BF950", 1, 6, 116, 7))

        assertEquals(GatewayOmciManagementEvidence(true, "10.0.0.5"), result)
        server.verify()
    }

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

    @Test
    fun `postForm envia el entorno del llamante`() {
        val restTemplate = RestTemplate()
        val server = MockRestServiceServer.createServer(restTemplate)
        server.expect(requestTo("http://127.0.0.1:8080/ispadmin/api/olt-gateway/onu/authorize_onu"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header(OltGatewayHttpClient.ENV_HEADER, "stg"))
            .andRespond(withSuccess("""{"status":true}""", MediaType.APPLICATION_JSON))

        val client = OltGatewayHttpClient(
            OltGatewayClientProperties().apply {
                apiKey = "stg-key"
                internalBaseUrl = "http://127.0.0.1:8080/ispadmin"
                callerEnv = "stg"
            },
            restTemplate,
        )

        client.postForm("/api/olt-gateway/onu/authorize_onu", org.springframework.util.LinkedMultiValueMap())
        server.verify()
    }
}
