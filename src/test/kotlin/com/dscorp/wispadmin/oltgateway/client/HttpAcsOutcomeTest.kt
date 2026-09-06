package com.dscorp.wispadmin.oltgateway.client

import com.dscorp.wispadmin.oltgateway.config.OltGatewayProperties
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
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

    @Test fun `list profiles accepts a JSON array`() {
        server.expect(requestTo("http://acs/api/acs/v1/profiles"))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header(HttpAcsCpeClient.HEADER, "dev-acs-key"))
            .andRespond(withSuccess("""[{"productClass":"F6600R"}]""", MediaType.APPLICATION_JSON))
        val response = client.listProfiles()
        assertEquals(HttpStatus.OK, response.statusCode)
        assertTrue(response.body!!.contains("F6600R"))
        server.verify()
    }

    @Test fun `delete profile forwards 204 and encodes product class`() {
        server.expect(requestTo("http://acs/api/acs/v1/profiles/F6600R"))
            .andExpect(method(HttpMethod.DELETE))
            .andRespond(withStatus(HttpStatus.NO_CONTENT))
        val response = client.deleteProfile("F6600R")
        assertEquals(HttpStatus.NO_CONTENT, response.statusCode)
        server.verify()
    }

    @Test fun `preview forwards ACS validation body`() {
        server.expect(requestTo("http://acs/api/acs/v1/profiles/preview"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withStatus(HttpStatus.BAD_REQUEST).body("""{"error":"CSV vacio"}""").contentType(MediaType.APPLICATION_JSON))
        val response = client.previewProfile("""{"csv":""}""")
        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertTrue(response.body!!.contains("CSV vacio"))
        server.verify()
    }
}
