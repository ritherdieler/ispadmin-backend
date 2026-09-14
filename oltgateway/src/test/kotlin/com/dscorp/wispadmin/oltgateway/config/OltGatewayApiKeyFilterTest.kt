package com.dscorp.wispadmin.oltgateway.config

import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import javax.servlet.http.HttpServletResponse

class OltGatewayApiKeyFilterTest {

    private val properties = mockk<OltGatewayProperties>()
    private val objectMapper = ObjectMapper()
    private val filter = OltGatewayApiKeyFilter(properties, objectMapper)

    @Test
    fun `permite health sin api key`() {
        val request = MockHttpServletRequest("GET", "/ispadmin/api/olt-gateway/health")
        request.contextPath = "/ispadmin"
        request.servletPath = "/api/olt-gateway/health"
        request.requestURI = "/ispadmin/api/olt-gateway/health"
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()

        filter.doFilter(request, response, chain)

        assertEquals(HttpServletResponse.SC_OK, response.status)
        assertEquals(request, chain.request)
    }

    @Test
    fun `rechaza autofind sin api key`() {
        every { properties.isValidApiKey(null) } returns false
        every { properties.isValidApiKey("") } returns false

        val request = MockHttpServletRequest("GET", "/ispadmin/api/olt-gateway/onus/autofind")
        request.contextPath = "/ispadmin"
        request.servletPath = "/api/olt-gateway/onus/autofind"
        request.requestURI = "/ispadmin/api/olt-gateway/onus/autofind"
        val response = MockHttpServletResponse()

        filter.doFilter(request, response, MockFilterChain())

        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, response.status)
    }

    @Test
    fun `acepta autofind con api key valida`() {
        every { properties.isValidApiKey("dev-olt-gateway-key") } returns true

        val request = MockHttpServletRequest("GET", "/ispadmin/api/olt-gateway/onus/autofind")
        request.contextPath = "/ispadmin"
        request.servletPath = "/api/olt-gateway/onus/autofind"
        request.requestURI = "/ispadmin/api/olt-gateway/onus/autofind"
        request.addHeader(OltGatewayApiKeyFilter.HEADER, "dev-olt-gateway-key")
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()

        filter.doFilter(request, response, chain)

        assertEquals(HttpServletResponse.SC_OK, response.status)
        assertEquals(request, chain.request)
    }

    @Test
    fun `acepta X-Token como alias SmartOLT`() {
        every { properties.isValidApiKey("dev-olt-gateway-key") } returns true

        val request = MockHttpServletRequest("GET", "/ispadmin/api/olt-gateway/onu/unconfigured_onus")
        request.contextPath = "/ispadmin"
        request.servletPath = "/api/olt-gateway/onu/unconfigured_onus"
        request.requestURI = "/ispadmin/api/olt-gateway/onu/unconfigured_onus"
        request.addHeader(OltGatewayApiKeyFilter.SMARTOLT_TOKEN_HEADER, "dev-olt-gateway-key")
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()

        filter.doFilter(request, response, chain)

        assertEquals(HttpServletResponse.SC_OK, response.status)
        assertEquals(request, chain.request)
    }
}
