package com.dscorp.wispadmin.traffic.config

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import javax.servlet.http.HttpServletResponse

class TrafficApiKeyFilterScopeTest {

    private val filter = TrafficApiKeyFilter(TrafficProperties().apply { apiKey = "dev-traffic-key" }, ObjectMapper())

    @Test
    fun `does not require traffic key on olt-gateway paths`() {
        val request = MockHttpServletRequest("GET", "/ispadmin/api/olt-gateway/health")
        request.contextPath = "/ispadmin"
        request.servletPath = "/api/olt-gateway/health"
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()

        filter.doFilter(request, response, chain)

        assertEquals(HttpServletResponse.SC_OK, response.status)
        assertEquals(request, chain.request)
    }

    @Test
    fun `does not require traffic key on acs paths`() {
        val request = MockHttpServletRequest("POST", "/ispadmin/api/acs/v1/cpe/provision")
        request.contextPath = "/ispadmin"
        request.servletPath = "/api/acs/v1/cpe/provision"
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()

        filter.doFilter(request, response, chain)

        assertEquals(HttpServletResponse.SC_OK, response.status)
        assertEquals(request, chain.request)
    }

    @Test
    fun `requires traffic key on traffic api paths`() {
        val request = MockHttpServletRequest("GET", "/ispadmin/api/traffic/v1/config")
        request.contextPath = "/ispadmin"
        request.servletPath = "/api/traffic/v1/config"
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()

        filter.doFilter(request, response, chain)

        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, response.status)
        assertTrue(response.contentAsString.contains("X-Traffic-Key"))
    }
}
