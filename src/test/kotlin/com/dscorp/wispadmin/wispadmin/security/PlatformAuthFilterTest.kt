package com.dscorp.wispadmin.wispadmin.security

import com.dscorp.wispadmin.observability.security.ObservabilitySessionTokenService
import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import javax.servlet.http.HttpServletResponse

class PlatformAuthFilterTest {

    private val sessionTokenService = mockk<ObservabilitySessionTokenService>()
    private val filter = PlatformAuthFilter(sessionTokenService, ObjectMapper())

    @Test
    fun `no exige bearer en rutas olt-gateway`() {
        val request = MockHttpServletRequest("GET", "/ispadmin/api/olt-gateway/health")
        request.contextPath = "/ispadmin"
        request.servletPath = "/api/olt-gateway/health"
        request.requestURI = "/ispadmin/api/olt-gateway/health"
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()

        filter.doFilter(request, response, chain)

        assertEquals(HttpServletResponse.SC_OK, response.status)
        assertEquals(request, chain.request)
        verify(exactly = 0) { sessionTokenService.verifyAccess(any()) }
    }

    @Test
    fun `sigue exigiendo bearer en rutas protegidas`() {
        every { sessionTokenService.verifyAccess(null) } returns null

        val request = MockHttpServletRequest("GET", "/ispadmin/subscription/all")
        request.servletPath = "/subscription/all"
        request.requestURI = "/ispadmin/subscription/all"
        val response = MockHttpServletResponse()

        filter.doFilter(request, response, MockFilterChain())

        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, response.status)
    }
}
