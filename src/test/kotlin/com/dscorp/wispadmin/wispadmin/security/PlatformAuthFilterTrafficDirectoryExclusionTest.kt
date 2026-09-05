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

class PlatformAuthFilterTrafficDirectoryExclusionTest {

    private val sessionTokenService = mockk<ObservabilitySessionTokenService>(relaxed = true)
    private val filter = PlatformAuthFilter(sessionTokenService, ObjectMapper())

    @Test
    fun `excluye el directorio interno de trafico del platform auth`() {
        val request = MockHttpServletRequest("GET", "/ispadmin-staging/internal/traffic/targets")
        request.contextPath = "/ispadmin-staging"
        request.servletPath = "/internal/traffic/targets"
        request.requestURI = "/ispadmin-staging/internal/traffic/targets"
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()

        filter.doFilter(request, response, chain)

        assertEquals(HttpServletResponse.SC_OK, response.status)
        assertEquals(request, chain.request)
        verify(exactly = 0) { sessionTokenService.verifyAccess(any()) }
    }
}
