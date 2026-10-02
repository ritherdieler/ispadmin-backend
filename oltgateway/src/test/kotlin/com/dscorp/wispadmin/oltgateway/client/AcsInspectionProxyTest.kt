package com.dscorp.wispadmin.oltgateway.client

import com.dscorp.wispadmin.oltgateway.config.OltGatewayProperties
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestTemplate

class AcsInspectionProxyTest {
    @Test fun `inspection uses caller environment and ACS authentication`() {
        val properties = OltGatewayProperties().apply {
            acs.baseUrlStaging = "http://staging-acs"
            acs.apiKey = "test-key"
        }
        val http = RestTemplate()
        val server = MockRestServiceServer.createServer(http)
        val proxy = AcsInspectionProxy(properties, http, jacksonObjectMapper())
        server.expect(requestTo("http://staging-acs/api/acs/v1/cpe/SN123/inspection/tree?parent=InternetGatewayDevice.LANDevice&q=SSID"))
            .andExpect(header("X-Acs-Key", "test-key"))
            .andRespond(withSuccess("""{"entries":[]}""", MediaType.APPLICATION_JSON))

        assertEquals(0, proxy.tree("SN123", "InternetGatewayDevice.LANDevice", "SSID", "stg").path("entries").size())
        server.verify()
    }
}
