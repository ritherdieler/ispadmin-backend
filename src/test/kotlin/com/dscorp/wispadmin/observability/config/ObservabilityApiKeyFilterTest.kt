package com.dscorp.wispadmin.observability.config

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

class ObservabilityApiKeyFilterTest {

    private val properties = mockk<ObservabilityProperties>()
    private val objectMapper = ObjectMapper()
    private val sessionTokenService = mockk<ObservabilitySessionTokenService>()
    private val filter = ObservabilityApiKeyFilter(properties, objectMapper, sessionTokenService)

    @Test
    fun `acepta ingest cuando servletPath esta vacio y requestURI incluye context path`() {
        every { properties.isValidApiKey("dev-obs-android-key") } returns true
        every { properties.platformForApiKey("dev-obs-android-key") } returns "android"

        val request = MockHttpServletRequest("POST", "/ispadmin/observability/events")
        request.contextPath = "/ispadmin"
        request.servletPath = ""
        request.requestURI = "/ispadmin/observability/events"
        request.addHeader(ObservabilityApiKeyFilter.HEADER, "dev-obs-android-key")
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()

        filter.doFilter(request, response, chain)

        assertEquals(HttpServletResponse.SC_OK, response.status)
        assertEquals(request, chain.request)
    }

    @Test
    fun `rechaza ingest sin api key aunque el path este en requestURI`() {
        every { properties.isValidApiKey(null) } returns false
        every { properties.isValidApiKey("") } returns false

        val request = MockHttpServletRequest("POST", "/ispadmin/observability/events")
        request.contextPath = "/ispadmin"
        request.servletPath = ""
        request.requestURI = "/ispadmin/observability/events"
        val response = MockHttpServletResponse()

        filter.doFilter(request, response, MockFilterChain())

        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, response.status)
        verify(exactly = 0) { sessionTokenService.verifyAdmin(any()) }
    }
}
