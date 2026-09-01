package com.dscorp.wispadmin.servicehealth

import com.dscorp.wispadmin.servicehealth.controller.HealthAccess
import com.dscorp.wispadmin.wispadmin.security.ObservabilitySessionClaims
import com.dscorp.wispadmin.wispadmin.security.ObservabilitySessionTokenService
import io.mockk.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.web.server.ResponseStatusException

class HealthAccessTest {
    private val tokens=mockk<ObservabilitySessionTokenService>()
    private val access=HealthAccess(tokens)
    @Test fun `anonymous user is rejected and headers cannot invent a role`() {
        every { tokens.verifyAccess(any()) } returns null
        val request=MockHttpServletRequest(); request.addHeader("X-Role","ADMIN")
        assertEquals(401,assertThrows(ResponseStatusException::class.java) { access.require(request) }.rawStatusCode)
    }
    @Test fun `technician can read but cannot authorize WAN or identity resolution`() {
        every { tokens.verifyAccess("token") } returns ObservabilitySessionClaims(1,"tech","TECHNICIAN",0,9999999999)
        val request=MockHttpServletRequest(); request.addHeader("Authorization","Bearer token")
        assertEquals("TECHNICIAN",access.require(request).role)
        assertEquals(403,assertThrows(ResponseStatusException::class.java) { access.require(request,true) }.rawStatusCode)
    }
    @Test fun `confirmation and idempotency are both required`() {
        val request=MockHttpServletRequest()
        assertThrows(ResponseStatusException::class.java) { access.confirmation(request) }
        request.addHeader("X-Confirm-Action","true"); request.addHeader("Idempotency-Key","unique-request-1")
        assertEquals("unique-request-1",access.confirmation(request))
    }
}
