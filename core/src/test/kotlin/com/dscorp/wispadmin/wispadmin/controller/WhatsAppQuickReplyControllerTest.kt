package com.dscorp.wispadmin.wispadmin.controller

import com.dscorp.wispadmin.wispadmin.dto.WhatsAppQuickReplyBody
import com.dscorp.wispadmin.wispadmin.dto.WhatsAppQuickReplyDto
import com.dscorp.wispadmin.wispadmin.security.PlatformAuthFilter
import com.dscorp.wispadmin.wispadmin.service.whatsapp.WhatsAppQuickReplyService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.mock.web.MockHttpServletRequest
import java.time.LocalDateTime

class WhatsAppQuickReplyControllerTest {

    private val service = mockk<WhatsAppQuickReplyService>()
    private lateinit var controller: WhatsAppQuickReplyController

    @BeforeEach
    fun setUp() {
        controller = WhatsAppQuickReplyController(service)
    }

    @Test
    fun `list requires authentication`() {
        val response = controller.list(MockHttpServletRequest())
        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
    }

    @Test
    fun `list returns quick replies for authenticated SECRETARY`() {
        every { service.list() } returns listOf(
            WhatsAppQuickReplyDto(1, "Saludo", "/saludo", "Hola", LocalDateTime.now(), LocalDateTime.now())
        )

        val response = controller.list(authenticatedRequest("SECRETARY"))

        assertEquals(HttpStatus.OK, response.statusCode)
        verify(exactly = 1) { service.list() }
    }

    @Test
    fun `create delegates to service for ADMIN`() {
        every { service.create(any()) } returns WhatsAppQuickReplyDto(
            2, "Cierre", "/cierre", "Gracias", LocalDateTime.now(), LocalDateTime.now()
        )

        val response = controller.create(
            WhatsAppQuickReplyBody(title = "Cierre", shortcut = "/cierre", content = "Gracias"),
            authenticatedRequest("ADMIN")
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        verify(exactly = 1) { service.create(any()) }
    }

    @Test
    fun `create forbidden for SECRETARY`() {
        val response = controller.create(
            WhatsAppQuickReplyBody(title = "Cierre", shortcut = "/cierre", content = "Gracias"),
            authenticatedRequest("SECRETARY")
        )

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        verify(exactly = 0) { service.create(any()) }
    }

    @Test
    fun `update allowed for ADMIN`() {
        every { service.update(3, any()) } returns WhatsAppQuickReplyDto(
            3, "Cierre", "/cierre", "Gracias", LocalDateTime.now(), LocalDateTime.now()
        )

        val response = controller.update(
            3,
            WhatsAppQuickReplyBody(title = "Cierre", shortcut = "/cierre", content = "Gracias"),
            authenticatedRequest("ADMIN")
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        verify(exactly = 1) { service.update(3, any()) }
    }

    @Test
    fun `update forbidden for SECRETARY`() {
        val response = controller.update(
            3,
            WhatsAppQuickReplyBody(title = "Cierre", shortcut = "/cierre", content = "Gracias"),
            authenticatedRequest("SECRETARY")
        )

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        verify(exactly = 0) { service.update(any(), any()) }
    }

    @Test
    fun `delete allowed for ADMIN`() {
        every { service.delete(4) } returns Unit

        val response = controller.delete(4, authenticatedRequest("ADMIN"))

        assertEquals(HttpStatus.OK, response.statusCode)
        verify(exactly = 1) { service.delete(4) }
    }

    @Test
    fun `delete forbidden for SECRETARY`() {
        val response = controller.delete(4, authenticatedRequest("SECRETARY"))

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        verify(exactly = 0) { service.delete(any()) }
    }

    private fun authenticatedRequest(userType: String): MockHttpServletRequest =
        MockHttpServletRequest().apply {
            setAttribute(PlatformAuthFilter.AUTH_USER_ID_ATTRIBUTE, 2)
            setAttribute(PlatformAuthFilter.AUTH_USER_TYPE_ATTRIBUTE, userType)
        }
}
