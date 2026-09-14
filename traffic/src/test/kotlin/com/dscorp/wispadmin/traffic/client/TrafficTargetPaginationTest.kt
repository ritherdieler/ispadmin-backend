package com.dscorp.wispadmin.traffic.client

import com.dscorp.wispadmin.traffic.config.TrafficProperties
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.*
import org.springframework.web.client.RestTemplate

class TrafficTargetPaginationTest {
    @Test fun `collects all cursor pages and never replaces cache with a partial refresh`() {
        val client = HttpTrafficDirectoryClient(TrafficProperties().apply {
            coreBaseUrl = "http://core"; directoryTtlSeconds = 0
        }, jacksonObjectMapper())
        val http = client.javaClass.getDeclaredField("restTemplate").apply { isAccessible = true }.get(client) as RestTemplate
        val server = MockRestServiceServer.createServer(http)
        fun page(after: Int, body: String) {
            server.expect(requestTo("http://core/internal/traffic/targets/page?after=$after&size=200"))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON))
        }
        page(0, """{"items":[{"subscriptionId":1,"ip":"192.0.2.1"}],"nextCursor":1}""")
        page(1, """{"items":[{"subscriptionId":2,"ip":"192.0.2.2"}],"nextCursor":null}""")
        assertEquals(listOf(1, 2), client.list().map { it.subscriptionId })
        server.verify(); server.reset()
        page(0, """{"items":[{"subscriptionId":3,"ip":"192.0.2.3"}],"nextCursor":3}""")
        server.expect(requestTo("http://core/internal/traffic/targets/page?after=3&size=200")).andRespond(withServerError())
        assertEquals(listOf(1, 2), client.list().map { it.subscriptionId })
        server.verify()
    }
}
