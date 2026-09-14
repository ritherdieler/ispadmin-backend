package com.dscorp.wispadmin.wispadmin.controller

import com.dscorp.wispadmin.wispadmin.oltclient.OltGatewayHttpClient
import com.dscorp.wispadmin.wispadmin.security.PlatformAuthFilter
import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockMultipartFile

class Tr069ModelProfileControllerTest {

    private val gateway = mockk<OltGatewayHttpClient>()
    private val objectMapper = ObjectMapper()
    private lateinit var controller: Tr069ModelProfileController

    @BeforeEach
    fun setUp() {
        controller = Tr069ModelProfileController(gateway, objectMapper)
    }

    @Test
    fun `list proxies gateway JSON for ADMIN`() {
        every { gateway.getJson("/api/olt-gateway/acs/profiles") } returns
            ResponseEntity.ok("""[{"productClass":"F6600R"}]""")
        val response = controller.list(adminRequest())
        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals("""[{"productClass":"F6600R"}]""", response.body)
    }

    @Test
    fun `list forbidden for SECRETARY`() {
        val response = controller.list(request("SECRETARY"))
        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
    }

    @Test
    fun `preview sends CSV JSON to gateway`() {
        every { gateway.postJsonBody("/api/olt-gateway/acs/profiles/preview", any()) } returns
            ResponseEntity.ok("""{"draft":{"productClass":"F6600R"},"readyToImport":true}""")
        val file = MockMultipartFile("file", "zte.csv", "text/csv", "csv-bytes".toByteArray())
        val response = controller.preview(file, adminRequest())
        assertEquals(HttpStatus.OK, response.statusCode)
        verify {
            gateway.postJsonBody(
                "/api/olt-gateway/acs/profiles/preview",
                match { it.contains("csv-bytes") },
            )
        }
    }

    @Test
    fun `delete proxies 204`() {
        every { gateway.deleteJson("/api/olt-gateway/acs/profiles/F6600R") } returns
            ResponseEntity.noContent().build()
        val response = controller.delete("F6600R", adminRequest())
        assertEquals(HttpStatus.NO_CONTENT, response.statusCode)
    }

    private fun adminRequest(): MockHttpServletRequest = request("ADMIN")

    private fun request(userType: String): MockHttpServletRequest {
        val request = MockHttpServletRequest()
        request.setAttribute(PlatformAuthFilter.AUTH_USER_TYPE_ATTRIBUTE, userType)
        request.setAttribute(PlatformAuthFilter.AUTH_USERNAME_ATTRIBUTE, "admin@test")
        return request
    }
}
