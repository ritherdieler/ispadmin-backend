package com.dscorp.wispadmin.oltgateway.config

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import javax.servlet.FilterChain
import javax.servlet.ServletRequest
import javax.servlet.ServletResponse
import javax.servlet.http.HttpServletResponse

class StagingLabWriteGuardTest {

    private val properties = OltGatewayProperties().apply {
        apiKey = "prod-key"
        stagingApiKey = "stg-key"
        writes.labAcsSnSuffixes = "0031C0B6,12345B4641531C0B6,ZTEGDC47BFFD"
    }
    private val filter = OltGatewayApiKeyFilter(properties, ObjectMapper())

    @Test
    fun `staging key y SN que no es lab responden 403`() {
        val response = postAuthorize("stg-key", "HWTC12345678")

        assertEquals(HttpServletResponse.SC_FORBIDDEN, response.status)
    }

    @Test
    fun `staging key y SN lab ZTEGDC47BFFD pasan`() {
        val response = postAuthorize("stg-key", "ZTEGDC47BFFD")

        assertEquals(HttpServletResponse.SC_OK, response.status)
    }

    @Test
    fun `prod key autoriza un SN que no es lab`() {
        val response = postAuthorize("prod-key", "HWTC12345678")

        assertEquals(HttpServletResponse.SC_OK, response.status)
    }

    @Test
    fun `staging key lee sin SN`() {
        val request = gatewayRequest("GET", "/api/olt-gateway/onu/unconfigured_onus")
        request.addHeader(OltGatewayApiKeyFilter.HEADER, "stg-key")
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()

        filter.doFilter(request, response, chain)

        assertEquals(HttpServletResponse.SC_OK, response.status)
        assertEquals(request, chain.request)
    }

    @Test
    fun `staging key con env prod responde 400`() {
        val request = gatewayRequest("POST", "/api/olt-gateway/onu/authorize_onu")
        request.addHeader(OltGatewayApiKeyFilter.HEADER, "stg-key")
        request.addHeader(OltGatewayApiKeyFilter.ENV_HEADER, "prod")
        request.addParameter("sn", "ZTEGDC47BFFD")
        val response = MockHttpServletResponse()

        filter.doFilter(request, response, MockFilterChain())

        assertEquals(HttpServletResponse.SC_BAD_REQUEST, response.status)
    }


    @Test
    fun `staging key marca inventario solo lab y lo limpia al salir`() {
        val during = labInventoryFlagDuring("stg-key", "/api/olt-gateway/onus/configured")

        assertTrue(during)
        assertFalse(GatewayCallContext.labInventoryOnly())
    }

    @Test
    fun `prod key no marca inventario solo lab`() {
        val during = labInventoryFlagDuring("prod-key", "/api/olt-gateway/onus/configured")

        assertFalse(during)
        assertFalse(GatewayCallContext.labInventoryOnly())
    }

    @Test
    fun `staging key puede registrar un serial nuevo sin el write guard`() {
        val request = gatewayRequest("POST", "/api/olt-gateway/onu/lab")
        request.addHeader(OltGatewayApiKeyFilter.HEADER, "stg-key")
        request.addHeader(OltGatewayApiKeyFilter.ENV_HEADER, "stg")
        request.contentType = "application/json"
        request.setContent("""{"sn":"HWTCNEWLAB01"}""".toByteArray())
        val response = MockHttpServletResponse()

        filter.doFilter(request, response, MockFilterChain())

        assertEquals(HttpServletResponse.SC_OK, response.status)
    }

    private fun postAuthorize(key: String, sn: String): MockHttpServletResponse {
        val request = gatewayRequest("POST", "/api/olt-gateway/onu/authorize_onu")
        request.addHeader(OltGatewayApiKeyFilter.HEADER, key)
        request.addHeader(OltGatewayApiKeyFilter.ENV_HEADER, if (key == "stg-key") "stg" else "prod")
        request.addParameter("sn", sn)
        val response = MockHttpServletResponse()
        filter.doFilter(request, response, MockFilterChain())
        return response
    }


    private fun labInventoryFlagDuring(key: String, path: String): Boolean {
        val request = gatewayRequest("GET", path)
        request.addHeader(OltGatewayApiKeyFilter.HEADER, key)
        var during = false
        val chain = object : FilterChain {
            override fun doFilter(request: ServletRequest, response: ServletResponse) {
                during = GatewayCallContext.labInventoryOnly()
            }
        }
        filter.doFilter(request, MockHttpServletResponse(), chain)
        return during
    }

    private fun gatewayRequest(method: String, path: String): MockHttpServletRequest {
        val request = MockHttpServletRequest(method, "/ispadmin$path")
        request.contextPath = "/ispadmin"
        request.servletPath = path
        request.requestURI = "/ispadmin$path"
        return request
    }
}
