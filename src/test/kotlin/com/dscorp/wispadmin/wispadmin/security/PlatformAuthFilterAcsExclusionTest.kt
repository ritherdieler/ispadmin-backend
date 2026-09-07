package com.dscorp.wispadmin.wispadmin.security

import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import javax.servlet.http.HttpServletResponse

class PlatformAuthFilterAcsExclusionTest {

    private val sessionTokenService = mockk<ObservabilitySessionTokenService>(relaxed = true)
    private val filter = PlatformAuthFilter(sessionTokenService, ObjectMapper())

    @Test
    fun `excluye rutas acs del platform auth`() {
        val request = MockHttpServletRequest("GET", "/ispadmin/api/acs/v1/cpe/ZTEGDC47BFFD/status")
        request.contextPath = "/ispadmin"
        request.servletPath = "/api/acs/v1/cpe/ZTEGDC47BFFD/status"
        request.requestURI = "/ispadmin/api/acs/v1/cpe/ZTEGDC47BFFD/status"
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()

        filter.doFilter(request, response, chain)

        assertEquals(HttpServletResponse.SC_OK, response.status)
        assertEquals(request, chain.request)
        verify(exactly = 0) { sessionTokenService.verifyAccess(any()) }
    }

    @Test
    fun `excluye provision acs del platform auth`() {
        val request = MockHttpServletRequest("POST", "/ispadmin/api/acs/v1/cpe/provision")
        request.contextPath = "/ispadmin"
        request.servletPath = "/api/acs/v1/cpe/provision"
        request.requestURI = "/ispadmin/api/acs/v1/cpe/provision"
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()

        filter.doFilter(request, response, chain)

        assertEquals(HttpServletResponse.SC_OK, response.status)
        assertEquals(request, chain.request)
        verify(exactly = 0) { sessionTokenService.verifyAccess(any()) }
    }
}
