package com.dscorp.wispadmin.wispadmin.service

import com.dscorp.wispadmin.wispadmin.response.OnuBySnResponse
import com.dscorp.wispadmin.wispadmin.util.OltHttpClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.http.client.ClientHttpRequestInterceptor
import org.springframework.test.web.client.ExpectedCount.once
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestTemplate

class RealOltServiceGatewayClientTest {

    @Test
    fun `RealOltService consume aliases del gateway solo por HTTP`() {
        val restTemplate = RestTemplate()
        val server = MockRestServiceServer.bindTo(restTemplate).build()
        val client = OltHttpClient(
            requestResponseLoggingInterceptor = ClientHttpRequestInterceptor { request, body, execution ->
                execution.execute(request, body)
            },
            baseUrl = "http://localhost:8080/ispadmin/api/olt-gateway/",
            apiKey = "dev-olt-gateway-key",
            provider = "gateway",
            authHeaderOverride = "",
            restTemplate = restTemplate
        )
        val service = RealOltService(client)

        server.expect(once(), requestTo("http://localhost:8080/ispadmin/api/olt-gateway/onu/unconfigured_onus"))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header("X-Olt-Gateway-Key", "dev-olt-gateway-key"))
            .andRespond(
                withSuccess(
                    """{"status":true,"response":[{"sn":"4857544311E70E9A","board":"0","port":"2","olt_id":"gigafiber-ma5608t","onu":"","onu_type_id":"","onu_type_name":"HG8245H","pon_type":"gpon"}]}""",
                    MediaType.APPLICATION_JSON
                )
            )

        server.expect(once(), requestTo("http://localhost:8080/ispadmin/api/olt-gateway/onu/get_onus_details_by_sn/4857544311E70E9A"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(
                withSuccess(
                    """{"status":true,"response_code":"200","onus":[]}""",
                    MediaType.APPLICATION_JSON
                )
            )

        val unconfigured = service.getUnConfiguredOnus()
        assertEquals(1, unconfigured?.size)
        assertEquals("4857544311E70E9A", unconfigured?.get(0)?.sn)

        val bySn: OnuBySnResponse = service.getOnuBySn("4857544311E70E9A")
        assertTrue(bySn.status)
        assertEquals("200", bySn.response_code)

        server.verify()
    }
}
