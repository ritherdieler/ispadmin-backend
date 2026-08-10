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
    fun `list returns quick replies for authenticated user`() {
        every { service.list() } returns listOf(
            WhatsAppQuickReplyDto(1, "Saludo", "/saludo", "Hola", LocalDateTime.now(), LocalDateTime.now())
        )

        val response = controller.list(authenticatedRequest())

        assertEquals(HttpStatus.OK, response.statusCode)
        verify(exactly = 1) { service.list() }
    }

    @Test
    fun `create delegates to service`() {
        every { service.create(any()) } returns WhatsAppQuickReplyDto(
            2, "Cierre", "/cierre", "Gracias", LocalDateTime.now(), LocalDateTime.now()
        )

        val response = controller.create(
            WhatsAppQuickReplyBody(title = "Cierre", shortcut = "/cierre", content = "Gracias"),
            authenticatedRequest()
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        verify(exactly = 1) { service.create(any()) }
    }

    private fun authenticatedRequest(): MockHttpServletRequest =
        MockHttpServletRequest().apply {
            setAttribute(PlatformAuthFilter.AUTH_USER_ID_ATTRIBUTE, 2)
            setAttribute(PlatformAuthFilter.AUTH_USER_TYPE_ATTRIBUTE, "ADMIN")
        }
}
