package com.dscorp.wispadmin.wispadmin.trafficclient

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
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

class TrafficRouterOsCommandUseCaseTest {

    @Test
    fun `print posts to Traffic routeros gateway`() {
        val restTemplate = RestTemplate()
        val server = MockRestServiceServer.createServer(restTemplate)
        server.expect(requestTo("http://127.0.0.1:8080/ispadmin/api/traffic/v1/routeros/8/print"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header(CoreTrafficApiKeyFilter.HEADER, "dev-traffic-key"))
            .andExpect(content().json("""{"path":"/queue/simple","query":{"target":"10.0.0.1/32"},"proplist":[]}"""))
            .andRespond(withSuccess("""{"rows":[{"name":"id:1"}]}""", MediaType.APPLICATION_JSON))

        val useCase = TrafficRouterOsCommandUseCase(
            TrafficHttpClient(
                TrafficClientProperties().apply {
                    apiKey = "dev-traffic-key"
                    internalBaseUrl = "http://127.0.0.1:8080/ispadmin"
                },
                restTemplate,
            ),
            ObjectMapper(),
        )

        val result = useCase.print(8, "/queue/simple", mapOf("target" to "10.0.0.1/32"), emptyList())

        assertTrue(result.isSuccess)
        assertEquals("id:1", result.getOrThrow().first()["name"])
        server.verify()
    }

    @Test
    fun `add posts write body to Traffic`() {
        val restTemplate = RestTemplate()
        val server = MockRestServiceServer.createServer(restTemplate)
        server.expect(requestTo("http://127.0.0.1:8080/ispadmin/api/traffic/v1/routeros/8/add"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(content().json("""{"path":"/ppp/secret","args":{"name":"gf6"}}"""))
            .andRespond(withSuccess("""{"ok":true}""", MediaType.APPLICATION_JSON))

        val useCase = TrafficRouterOsCommandUseCase(
            TrafficHttpClient(
                TrafficClientProperties().apply {
                    apiKey = "dev-traffic-key"
                    internalBaseUrl = "http://127.0.0.1:8080/ispadmin"
                },
                restTemplate,
            ),
            ObjectMapper(),
        )

        assertTrue(useCase.add(8, "/ppp/secret", mapOf("name" to "gf6")).isSuccess)
        server.verify()
    }
}
