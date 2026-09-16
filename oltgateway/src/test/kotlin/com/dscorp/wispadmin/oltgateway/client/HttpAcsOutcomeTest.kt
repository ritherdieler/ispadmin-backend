package com.dscorp.wispadmin.oltgateway.client

import com.dscorp.wispadmin.oltgateway.config.OltGatewayProperties
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestTemplate
import java.net.SocketTimeoutException

class HttpAcsOutcomeTest {
    private val http = RestTemplate()
    private val server = MockRestServiceServer.createServer(http)
    private val client = HttpAcsCpeClient(
        OltGatewayProperties().apply {
            acs.internalBaseUrl = "http://acs"
            acs.apiKey = "dev-acs-key"
        },
        http,
        jacksonObjectMapper(),
    )

    @Test fun `transport timeout is an uncertain outcome and cannot become a terminal failure`() {
        server.expect(requestTo("http://acs/api/acs/v1/cpe/provision")).andRespond { throw SocketTimeoutException("fixture") }
        assertThrows(RestClientException::class.java) { client.provision(AcsCpeProvisionRequest("SN")) }
        server.verify()
    }

    @Test fun `unknown provider status is rejected instead of fabricated pending`() {
        server.expect(org.springframework.test.web.client.match.MockRestRequestMatchers.anything())
            .andRespond(withSuccess("""{"sn":"SN","status":"FUTURE_STATE"}""", MediaType.APPLICATION_JSON))
        assertThrows(RestClientException::class.java) { client.provision(AcsCpeProvisionRequest("SN")) }
    }

    @Test fun `serial path is encoded once and status absence is distinct from outage`() {
        server.expect(requestTo("http://acs/api/acs/v1/cpe/A%2FB%20C/status")).andRespond(withStatus(org.springframework.http.HttpStatus.NOT_FOUND))
        assertNull(client.status("A/B C"))
        server.verify()
    }

    @Test fun `malformed successful response cannot finish an activation`() {
        server.expect(org.springframework.test.web.client.match.MockRestRequestMatchers.anything())
            .andRespond(withSuccess("<html>proxy</html>", MediaType.TEXT_HTML))
        assertThrows(RestClientException::class.java) { client.provision(AcsCpeProvisionRequest("SN")) }
    }
}
