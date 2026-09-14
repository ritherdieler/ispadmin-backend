package com.dscorp.wispadmin.wispadmin.trafficclient

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

class TrafficHttpClientTest {

    @Test
    fun `getJson llama traffic WAR con X-Traffic-Key`() {
        val restTemplate = RestTemplate()
        val server = MockRestServiceServer.createServer(restTemplate)
        server.expect(requestTo("http://127.0.0.1:8080/ispadmin-staging-traffic/api/traffic/v1/by-subscription/2360/latest"))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header(CoreTrafficApiKeyFilter.HEADER, "dev-traffic-key"))
            .andRespond(withSuccess("""{"subscriptionId":2360,"ip":"192.168.250.20","sampleStatus":"OK"}""", MediaType.APPLICATION_JSON))

        val client = TrafficHttpClient(
            TrafficClientProperties().apply {
                apiKey = "dev-traffic-key"
                internalBaseUrl = "http://127.0.0.1:8080/ispadmin-staging-traffic"
            },
            restTemplate,
        )

        val body = client.getJson("/api/traffic/v1/by-subscription/2360/latest").body
        assertEquals(true, body?.contains("192.168.250.20"))
        server.verify()
    }
}
