package com.dscorp.wispadmin.netdiag.config

import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import javax.servlet.http.HttpServletResponse

class NetDiagApiKeyFilterTest {

    private val properties = mockk<NetDiagProperties>()
    private val objectMapper = ObjectMapper()
    private val filter = NetDiagApiKeyFilter(properties, objectMapper)

    @Test
    fun `permite health sin api key`() {
        val request = MockHttpServletRequest("GET", "/ispadmin/api/netdiag/health")
        request.contextPath = "/ispadmin"
        request.servletPath = "/api/netdiag/health"
        request.requestURI = "/ispadmin/api/netdiag/health"
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()

        filter.doFilter(request, response, chain)

        assertEquals(HttpServletResponse.SC_OK, response.status)
        assertEquals(request, chain.request)
    }

    @Test
    fun `rechaza incidents sin api key`() {
        every { properties.isValidApiKey(null) } returns false
        every { properties.isValidApiKey("") } returns false

        val request = MockHttpServletRequest("GET", "/ispadmin/api/netdiag/incidents")
        request.contextPath = "/ispadmin"
        request.servletPath = "/api/netdiag/incidents"
        request.requestURI = "/ispadmin/api/netdiag/incidents"
        val response = MockHttpServletResponse()

        filter.doFilter(request, response, MockFilterChain())

        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, response.status)
    }

    @Test
    fun `acepta incidents con api key valida`() {
        every { properties.isValidApiKey("dev-netdiag-key") } returns true

        val request = MockHttpServletRequest("GET", "/ispadmin/api/netdiag/incidents")
        request.contextPath = "/ispadmin"
        request.servletPath = "/api/netdiag/incidents"
        request.requestURI = "/ispadmin/api/netdiag/incidents"
        request.addHeader(NetDiagApiKeyFilter.HEADER, "dev-netdiag-key")
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()

        filter.doFilter(request, response, chain)

        assertEquals(HttpServletResponse.SC_OK, response.status)
        assertEquals(request, chain.request)
    }
}
