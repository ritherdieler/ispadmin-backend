package com.dscorp.wispadmin.wispadmin.trafficclient
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.*
import org.springframework.test.web.client.response.MockRestResponseCreators.*
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestTemplate
class InternalHttpContractTest {
    @Test fun `an encoded query is forwarded exactly once`() {
        val http=RestTemplate()
        val server=MockRestServiceServer.createServer(http)
        val client=TrafficHttpClient(TrafficClientProperties().apply { internalBaseUrl="http://traffic" },http)
        server.expect(requestTo("http://traffic/api/traffic/v1/subscriptions?search=A%26B%2BC"))
            .andRespond(withSuccess("{}",MediaType.APPLICATION_JSON))
        client.getJson("/api/traffic/v1/subscriptions","search=A%26B%2BC")
        server.verify()
    }
    @Test fun `a success status with invalid JSON is a contract failure`() {
        val http=RestTemplate()
        val server=MockRestServiceServer.createServer(http)
        val client=TrafficHttpClient(TrafficClientProperties().apply { internalBaseUrl="http://traffic" },http)
        server.expect(anything()).andRespond(withSuccess("not-json",MediaType.APPLICATION_JSON))
        assertThrows(RestClientException::class.java) { client.getJson("/api/traffic/v1/config") }
    }
}
