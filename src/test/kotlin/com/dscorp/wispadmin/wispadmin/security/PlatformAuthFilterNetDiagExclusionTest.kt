package com.dscorp.wispadmin.wispadmin.security

import com.dscorp.wispadmin.observability.security.ObservabilitySessionTokenService
import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import javax.servlet.http.HttpServletResponse

class PlatformAuthFilterNetDiagExclusionTest {

    private val sessionTokenService = mockk<ObservabilitySessionTokenService>(relaxed = true)
    private val filter = PlatformAuthFilter(sessionTokenService, ObjectMapper())

    @Test
    fun `excluye rutas netdiag del platform auth`() {
        val request = MockHttpServletRequest("GET", "/ispadmin/api/netdiag/health")
        request.contextPath = "/ispadmin"
        request.servletPath = "/api/netdiag/health"
        request.requestURI = "/ispadmin/api/netdiag/health"
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()

        filter.doFilter(request, response, chain)

        assertEquals(HttpServletResponse.SC_OK, response.status)
        assertEquals(request, chain.request)
        verify(exactly = 0) { sessionTokenService.verifyAccess(any()) }
    }

    @Test
    fun `excluye subrutas netdiag del platform auth`() {
        val request = MockHttpServletRequest("GET", "/ispadmin/api/netdiag/incidents/1")
        request.contextPath = "/ispadmin"
        request.servletPath = "/api/netdiag/incidents/1"
        request.requestURI = "/ispadmin/api/netdiag/incidents/1"
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()

        filter.doFilter(request, response, chain)

        assertEquals(HttpServletResponse.SC_OK, response.status)
        assertEquals(request, chain.request)
        verify(exactly = 0) { sessionTokenService.verifyAccess(any()) }
    }
}
