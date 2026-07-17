package com.dscorp.wispadmin.wispadmin.security

import com.dscorp.wispadmin.observability.security.ObservabilitySessionTokenService
import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import javax.servlet.http.HttpServletResponse

class PlatformAuthFilterSwaggerTest {

    private val sessionTokenService = mockk<ObservabilitySessionTokenService>()
    private val filter = PlatformAuthFilter(sessionTokenService, ObjectMapper())

    @ParameterizedTest
    @ValueSource(
        strings = [
            "/swagger-ui.html",
            "/swagger-ui/index.html",
            "/swagger-ui/swagger-ui.css",
            "/v3/api-docs",
            "/v3/api-docs.yaml",
            "/v3/api-docs/swagger-config",
            "/webjars/swagger-ui/index.html"
        ]
    )
    fun `no exige bearer en rutas swagger y api-docs`(servletPath: String) {
        val request = MockHttpServletRequest("GET", "/ispadmin$servletPath")
        request.contextPath = "/ispadmin"
        request.servletPath = servletPath
        request.requestURI = "/ispadmin$servletPath"
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()

        filter.doFilter(request, response, chain)

        assertEquals(HttpServletResponse.SC_OK, response.status)
        assertEquals(request, chain.request)
        verify(exactly = 0) { sessionTokenService.verifyAccess(any()) }
    }
}
