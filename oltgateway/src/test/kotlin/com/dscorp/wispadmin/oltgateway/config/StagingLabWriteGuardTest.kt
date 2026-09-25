package com.dscorp.wispadmin.oltgateway.config

import com.dscorp.wispadmin.events.EventRouteContext
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
    fun `la misma key con env lpstg sigue limitada a lab`() {
        val shared = OltGatewayProperties().apply {
            apiKey = "shared-key"
            stagingApiKey = "shared-key"
            writes.labAcsSnSuffixes = "ZTEGDC47BFFD"
        }
        val sharedFilter = OltGatewayApiKeyFilter(shared, ObjectMapper())
        val request = gatewayRequest("POST", "/api/olt-gateway/acs/cpe-inform")
        request.addHeader(OltGatewayApiKeyFilter.HEADER, "shared-key")
        request.addHeader(OltGatewayApiKeyFilter.ENV_HEADER, "lpstg")
        request.contentType = "application/json"
        request.setContent("""{"sn":"HWTC12345678","deviceId":"dev"}""".toByteArray())
        val response = MockHttpServletResponse()

        sharedFilter.doFilter(request, response, MockFilterChain())

        assertEquals(HttpServletResponse.SC_FORBIDDEN, response.status)
    }

    @Test
    fun `env lpstg publica en el stream lpstg`() {
        val request = gatewayRequest("GET", "/api/olt-gateway/onus/configured")
        request.addHeader(OltGatewayApiKeyFilter.HEADER, "stg-key")
        request.addHeader(OltGatewayApiKeyFilter.ENV_HEADER, "lpstg")
        var namespace: String? = null
        val chain = object : FilterChain {
            override fun doFilter(request: ServletRequest, response: ServletResponse) {
                namespace = EventRouteContext.namespace()
            }
        }

        filter.doFilter(request, MockHttpServletResponse(), chain)

        assertEquals("lpstg", namespace)
        assertEquals(null, EventRouteContext.namespace())
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

    @Test
    fun `staging key rechaza un inform que no es lab`() {
        val response = postInform("stg-key", "HWTC12345678")

        assertEquals(HttpServletResponse.SC_FORBIDDEN, response.status)
    }

    @Test
    fun `staging key deja pasar un inform lab`() {
        val response = postInform("stg-key", "ZTEGDC47BFFD")

        assertEquals(HttpServletResponse.SC_OK, response.status)
    }

    @Test
    fun `prod key deja pasar un inform que no es lab`() {
        val response = postInform("prod-key", "HWTC12345678")

        assertEquals(HttpServletResponse.SC_OK, response.status)
    }

    @Test
    fun `staging key autoriza por el sn del cuerpo en provisioning authorize`() {
        val request = gatewayRequest("POST", "/api/olt-gateway/onus/provisioning/authorize")
        request.addHeader(OltGatewayApiKeyFilter.HEADER, "stg-key")
        request.addHeader(OltGatewayApiKeyFilter.ENV_HEADER, "stg")
        request.contentType = "application/json"
        request.setContent("""{"sn":"ZTEGDC47BFFD","operationId":"op-1"}""".toByteArray())
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

    private fun postInform(key: String, sn: String): MockHttpServletResponse {
        val request = gatewayRequest("POST", "/api/olt-gateway/acs/cpe-inform")
        request.addHeader(OltGatewayApiKeyFilter.HEADER, key)
        request.addHeader(OltGatewayApiKeyFilter.ENV_HEADER, if (key == "stg-key") "stg" else "prod")
        request.contentType = "application/json"
        request.setContent("""{"sn":"$sn","deviceId":"dev"}""".toByteArray())
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
