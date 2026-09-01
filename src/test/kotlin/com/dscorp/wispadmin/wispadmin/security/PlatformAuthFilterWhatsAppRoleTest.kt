package com.dscorp.wispadmin.wispadmin.security

import com.dscorp.wispadmin.wispadmin.security.ObservabilitySessionClaims
import com.dscorp.wispadmin.wispadmin.security.ObservabilitySessionTokenService
import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import java.time.Instant
import javax.servlet.http.HttpServletResponse

class PlatformAuthFilterWhatsAppRoleTest {

    private val sessionTokenService = mockk<ObservabilitySessionTokenService>()
    private val filter = PlatformAuthFilter(sessionTokenService, ObjectMapper())

    @BeforeEach
    fun setUp() {
        every { sessionTokenService.verifyAccess(any()) } returns null
    }

    @Test
    fun `whatsapp sin token responde 401`() {
        val request = authenticatedRequest("GET", "/whatsapp/conversations", token = null)
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()

        filter.doFilter(request, response, chain)

        assertEquals(HttpStatus.UNAUTHORIZED.value(), response.status)
        assertTrue(response.contentAsString.contains("unauthorized"))
        assertEquals(null, chain.request)
    }

    @Test
    fun `whatsapp con rol TECHNICIAN responde 403`() {
        stubClaims("TECHNICIAN", "tech1")
        val request = authenticatedRequest("GET", "/whatsapp/conversations", token = "tok")
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()

        filter.doFilter(request, response, chain)

        assertEquals(HttpStatus.FORBIDDEN.value(), response.status)
        assertTrue(response.contentAsString.contains("forbidden"))
        assertEquals(null, chain.request)
    }

    @Test
    fun `whatsapp con rol SECRETARY deja pasar`() {
        stubClaims("SECRETARY", "sec1")
        val request = authenticatedRequest("GET", "/whatsapp/conversations", token = "tok")
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()

        filter.doFilter(request, response, chain)

        assertEquals(HttpServletResponse.SC_OK, response.status)
        assertEquals(request, chain.request)
        assertEquals("SECRETARY", request.getAttribute(PlatformAuthFilter.AUTH_USER_TYPE_ATTRIBUTE))
        assertEquals("sec1", request.getAttribute(PlatformAuthFilter.AUTH_USERNAME_ATTRIBUTE))
    }

    @Test
    fun `whatsapp con rol ADMIN deja pasar`() {
        stubClaims("ADMIN", "admin1")
        val request = authenticatedRequest("POST", "/whatsapp/conversations/51999999999/reply", token = "tok")
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()

        filter.doFilter(request, response, chain)

        assertEquals(HttpServletResponse.SC_OK, response.status)
        assertEquals(request, chain.request)
    }

    @Test
    fun `crm con rol SALES responde 403`() {
        stubClaims("SALES", "sales1")
        val request = authenticatedRequest("GET", "/crm/conversations", token = "tok")
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()

        filter.doFilter(request, response, chain)

        assertEquals(HttpStatus.FORBIDDEN.value(), response.status)
        assertEquals(null, chain.request)
    }

    @Test
    fun `crm con rol ADMIN deja pasar`() {
        stubClaims("ADMIN", "admin1")
        val request = authenticatedRequest("GET", "/crm/conversations", token = "tok")
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()

        filter.doFilter(request, response, chain)

        assertEquals(HttpServletResponse.SC_OK, response.status)
        assertEquals(request, chain.request)
    }

    @Test
    fun `webhook whatsapp sigue publico sin token`() {
        val request = MockHttpServletRequest("GET", "/ispadmin/whatsapp/webhook")
        request.contextPath = "/ispadmin"
        request.servletPath = "/whatsapp/webhook"
        request.requestURI = "/ispadmin/whatsapp/webhook"
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()

        filter.doFilter(request, response, chain)

        assertEquals(HttpServletResponse.SC_OK, response.status)
        assertEquals(request, chain.request)
        verify(exactly = 0) { sessionTokenService.verifyAccess(any()) }
    }

    private fun stubClaims(type: String, username: String) {
        every { sessionTokenService.verifyAccess("tok") } returns ObservabilitySessionClaims(
            userId = 1,
            username = username,
            type = type,
            iat = Instant.now().epochSecond,
            exp = Instant.now().epochSecond + 3600,
            typ = ObservabilitySessionTokenService.TYPE_ACCESS
        )
    }

    private fun authenticatedRequest(method: String, servletPath: String, token: String?): MockHttpServletRequest {
        val request = MockHttpServletRequest(method, "/ispadmin$servletPath")
        request.contextPath = "/ispadmin"
        request.servletPath = servletPath
        request.requestURI = "/ispadmin$servletPath"
        if (token != null) {
            request.addHeader("Authorization", "Bearer $token")
        }
        return request
    }
}
