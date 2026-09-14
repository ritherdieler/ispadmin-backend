package com.dscorp.wispadmin.netdiag.client

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestTemplate

class NetDiagGatewayUriEncodingTest {
    @Test
    fun `olt names are encoded once in inventory queries`() {
        val client = NetDiagOltGatewayHttpClient("http://gateway", "key")
        val field = client.javaClass.getDeclaredField("restTemplate").apply { isAccessible = true }
        val server = MockRestServiceServer.createServer(field.get(client) as RestTemplate)
        server.expect(requestTo("http://gateway/api/olt-gateway/olts/id-by-name?name=OLT%26lab"))
            .andRespond(withSuccess("""{"id":4}""", MediaType.APPLICATION_JSON))
        server.expect(requestTo("http://gateway/api/olt-gateway/onus/pon?oltName=OLT%26lab&board=0&port=1"))
            .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON))
        assertEquals(4L, client.findOltId("OLT&lab"))
        assertEquals(emptyList<Any>(), client.listOnusOnPon("OLT&lab", 0, 1))
        server.verify()
    }
}
