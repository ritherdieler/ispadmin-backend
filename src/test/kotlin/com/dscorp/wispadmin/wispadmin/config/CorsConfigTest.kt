package com.dscorp.wispadmin.wispadmin.config

import com.dscorp.wispadmin.wispadmin.security.ObservabilitySessionTokenService
import com.dscorp.wispadmin.wispadmin.security.PlatformAuthFilter
import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.springframework.core.Ordered
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.web.filter.CorsFilter
import javax.servlet.FilterChain
import javax.servlet.http.HttpServletResponse

class CorsConfigTest {

    @Test
    fun `corsFilter registration runs at highest precedence before platform auth`() {
        val config = CorsConfig()
        val registration = config.corsFilter(config.corsConfigurationSource())

        assertEquals(Ordered.HIGHEST_PRECEDENCE, registration.order)
        assertNotNull(registration.filter)
        assertEquals(CorsFilter::class.java, registration.filter::class.java)
    }

    @Test
    fun `unauthorized smart-map response keeps Access-Control-Allow-Origin for backoffice`() {
        val config = CorsConfig()
        val corsFilter = config.corsFilter(config.corsConfigurationSource()).filter
        val sessionTokenService = mockk<ObservabilitySessionTokenService>()
        every { sessionTokenService.verifyAccess(any()) } returns null
        val authFilter = PlatformAuthFilter(sessionTokenService, ObjectMapper())

        val request = MockHttpServletRequest("GET", "/ispadmin/smart-map/collection-places")
        request.contextPath = "/ispadmin"
        request.servletPath = "/smart-map/collection-places"
        request.requestURI = "/ispadmin/smart-map/collection-places"
        request.addHeader("Origin", "https://backoffice.gigafiberperu.cloud")
        request.queryString = "userType=ADMIN"

        val response = MockHttpServletResponse()
        val terminal = MockFilterChain()
        val chainAfterCors = FilterChain { req, res ->
            authFilter.doFilter(req, res, terminal)
        }

        corsFilter.doFilter(request, response, chainAfterCors)

        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, response.status)
        assertEquals(
            "https://backoffice.gigafiberperu.cloud",
            response.getHeader("Access-Control-Allow-Origin"),
        )
    }
}
