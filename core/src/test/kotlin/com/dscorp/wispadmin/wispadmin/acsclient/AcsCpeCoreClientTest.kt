package com.dscorp.wispadmin.wispadmin.acsclient

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.content
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestTemplate
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

class AcsCpeCoreClientTest {

    @Test
    fun `provision sends PPPoE credentials with null wifi so SSID is not touched`() {
        val restTemplate = RestTemplate()
        val server = MockRestServiceServer.createServer(restTemplate)
        server.expect(requestTo("http://127.0.0.1:8091/ispadmin-staging-acs/api/acs/v1/cpe/provision"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header(AcsHttpClient.HEADER, "dev-acs-key"))
            .andExpect(content().json("""{"sn":"ZTEGDC47BFFD","wanVlanId":100,"pppoeUsername":"gf1001","pppoePassword":"secret"}"""))
            .andRespond(withSuccess("""{"sn":"ZTEGDC47BFFD","status":"PENDING"}""", MediaType.APPLICATION_JSON))

        val client = AcsCpeCoreClient(
            AcsHttpClient(
                AcsClientProperties().apply {
                    apiKey = "dev-acs-key"
                    internalBaseUrl = "http://127.0.0.1:8091/ispadmin-staging-acs"
                },
                restTemplate,
            ),
            jacksonObjectMapper(),
        )

        val result = client.provision(
            CoreCpeProvisionRequest(
                sn = "ZTEGDC47BFFD",
                wanVlanId = 100,
                pppoeUsername = "gf1001",
                pppoePassword = "secret",
            )
        )
        assertEquals("PENDING", result.status)
        server.verify()
    }

    @Test
    fun `accessLayout reads WAN paths and ConnectionRequestURL`() {
        val restTemplate = RestTemplate()
        val server = MockRestServiceServer.createServer(restTemplate)
        server.expect(requestTo("http://127.0.0.1:8091/ispadmin-staging-acs/api/acs/v1/cpe/ZTEGDC47BFFD/access-layout"))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header(AcsHttpClient.HEADER, "dev-acs-key"))
            .andRespond(
                withSuccess(
                    """{"sn":"ZTEGDC47BFFD","productClass":"F6600R","connectionRequestUrl":"http://192.168.253.10:7547/","hasPppPath":true,"wanIpSharesPppSlot":true}""",
                    MediaType.APPLICATION_JSON,
                )
            )

        val client = AcsCpeCoreClient(
            AcsHttpClient(
                AcsClientProperties().apply {
                    apiKey = "dev-acs-key"
                    internalBaseUrl = "http://127.0.0.1:8091/ispadmin-staging-acs"
                },
                restTemplate,
            ),
            jacksonObjectMapper(),
        )

        val layout = client.accessLayout("ZTEGDC47BFFD")
        assertEquals("F6600R", layout.productClass)
        assertEquals(true, layout.hasPppPath)
        assertEquals(true, layout.wanIpSharesPppSlot)
        server.verify()
    }
}
