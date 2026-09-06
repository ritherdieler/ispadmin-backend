package com.dscorp.wispadmin.traffic.client
import com.dscorp.wispadmin.traffic.config.TrafficProperties
import com.dscorp.wispadmin.traffic.port.TrafficDirectoryTarget
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.anything
import org.springframework.test.web.client.response.MockRestResponseCreators.withServerError
import org.springframework.web.client.RestTemplate
class TrafficDirectoryStalenessTest {
    @Test fun `an outage cannot keep old target assignments alive indefinitely`() {
        val client=HttpTrafficDirectoryClient(TrafficProperties().apply { coreBaseUrl="http://core"; directoryTtlSeconds=0 },jacksonObjectMapper())
        fun field(name: String)=client.javaClass.getDeclaredField(name).apply { isAccessible=true }
        field("cached").set(client,listOf(TrafficDirectoryTarget(1,"192.0.2.1")))
        field("cachedAtMs").set(client,System.currentTimeMillis()-3_600_000)
        val server=MockRestServiceServer.createServer(field("restTemplate").get(client) as RestTemplate)
        server.expect(anything()).andRespond(withServerError())
        assertThrows(Exception::class.java) { client.list() }
        server.verify()
    }
}
