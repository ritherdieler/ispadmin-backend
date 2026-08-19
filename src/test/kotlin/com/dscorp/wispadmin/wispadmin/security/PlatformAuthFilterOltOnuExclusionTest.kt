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

class PlatformAuthFilterOltOnuExclusionTest {

    private val sessionTokenService = mockk<ObservabilitySessionTokenService>(relaxed = true)
    private val filter = PlatformAuthFilter(sessionTokenService, ObjectMapper())

    @Test
    fun `excluye alias SmartOLT bajo api onu del platform auth`() {
        val request = MockHttpServletRequest("GET", "/ispadmin/api/onu/unconfigured_onus")
        request.contextPath = "/ispadmin"
        request.servletPath = "/api/onu/unconfigured_onus"
        request.requestURI = "/ispadmin/api/onu/unconfigured_onus"
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()

        filter.doFilter(request, response, chain)

        assertEquals(HttpServletResponse.SC_OK, response.status)
        assertEquals(request, chain.request)
        verify(exactly = 0) { sessionTokenService.verifyAccess(any()) }
    }

    @Test
    fun `excluye set_wan_mode bajo api onu del platform auth`() {
        val request = MockHttpServletRequest("POST", "/ispadmin/api/onu/set_wan_mode/gigafiber-ma5608t_1_0_5")
        request.contextPath = "/ispadmin"
        request.servletPath = "/api/onu/set_wan_mode/gigafiber-ma5608t_1_0_5"
        request.requestURI = "/ispadmin/api/onu/set_wan_mode/gigafiber-ma5608t_1_0_5"
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()

        filter.doFilter(request, response, chain)

        assertEquals(HttpServletResponse.SC_OK, response.status)
        verify(exactly = 0) { sessionTokenService.verifyAccess(any()) }
    }
}
