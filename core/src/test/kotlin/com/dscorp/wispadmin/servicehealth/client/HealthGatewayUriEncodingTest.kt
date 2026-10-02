package com.dscorp.wispadmin.servicehealth.client

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestTemplate

class HealthGatewayUriEncodingTest {
    @Test
    fun `inspection forwards the configured caller environment`() {
        val client = HealthOltGatewayHttpClient("http://gateway", "key", "stg")
        val field = client.javaClass.getDeclaredField("restTemplate").apply { isAccessible = true }
        val server = MockRestServiceServer.createServer(field.get(client) as RestTemplate)
        server.expect(requestTo("http://gateway/api/olt-gateway/onus/SN1/cpe/inspection/summary"))
            .andExpect(header("X-Gigafiber-Env", "stg"))
            .andRespond(withSuccess("""{"deviceId":"device-1"}""", MediaType.APPLICATION_JSON))
        assertEquals("device-1", client.summary("SN1").path("deviceId").asText())
        server.verify()
    }
    @Test
    fun `serials and names are encoded once in gateway queries`() {
        val client = HealthOltGatewayHttpClient("http://gateway", "key")
        val field = client.javaClass.getDeclaredField("restTemplate").apply { isAccessible = true }
        val server = MockRestServiceServer.createServer(field.get(client) as RestTemplate)
        server.expect(requestTo("http://gateway/api/olt-gateway/health-onus/by-sn?sn=A%2FB%20C"))
            .andRespond(withSuccess("""{"id":1,"sn":"A/B C"}""", MediaType.APPLICATION_JSON))
        server.expect(requestTo("http://gateway/api/olt-gateway/olts/id-by-name?name=OLT%26lab"))
            .andRespond(withSuccess("""{"id":9}""", MediaType.APPLICATION_JSON))
        server.expect(requestTo("http://gateway/api/olt-gateway/onus/A%2FB%20C/cpe/telemetry"))
            .andRespond(withSuccess("""{"sn":"A/B C","cpeStatus":"NA"}""", MediaType.APPLICATION_JSON))
        assertEquals("A/B C", client.findBySn("A/B C")!!.sn)
        assertEquals(9L, client.findOltIdByName("OLT&lab"))
        assertEquals("A/B C", client.telemetry("A/B C")!!.sn)
        server.verify()
    }
}
