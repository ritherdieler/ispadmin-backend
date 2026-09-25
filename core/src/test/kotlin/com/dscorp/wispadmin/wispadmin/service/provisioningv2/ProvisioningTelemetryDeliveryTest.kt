package com.dscorp.wispadmin.wispadmin.service.provisioningv2

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.*
import org.springframework.test.web.client.response.MockRestResponseCreators.*
import org.springframework.web.client.RestTemplate

class ProvisioningTelemetryDeliveryTest {
    @Test fun `requires durable acknowledgement instead of trusting HTTP 202`() {
        val http = RestTemplate()
        val server = MockRestServiceServer.createServer(http)
        val client = ProvisioningTelemetryDelivery(http, jacksonObjectMapper(), "https://obs.example/core", "test-key")
        server.expect(requestTo("https://obs.example/core/observability/events"))
            .andExpect(method(HttpMethod.POST)).andExpect(header("X-Obs-Delivery-Id", "op:17"))
            .andRespond(withStatus(HttpStatus.ACCEPTED).contentType(MediaType.APPLICATION_JSON)
                .body("""{"accepted":1,"rejected":0}""").headers(org.springframework.http.HttpHeaders().apply { set("X-Obs-Delivery-Id", "op:17") }))
        server.expect(requestTo("https://obs.example/core/observability/events"))
            .andRespond(withStatus(HttpStatus.ACCEPTED).contentType(MediaType.APPLICATION_JSON)
                .body("""{"accepted":0,"rejected":1}""").headers(org.springframework.http.HttpHeaders().apply { set("X-Obs-Delivery-Id", "op:18") }))
        assertTrue(client.send("op:17", "{}"))
        assertFalse(client.send("op:18", "{}"))
        server.verify()
    }
}
