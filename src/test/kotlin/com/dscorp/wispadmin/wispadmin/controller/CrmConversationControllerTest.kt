package com.dscorp.wispadmin.wispadmin.controller

import com.dscorp.wispadmin.wispadmin.dto.CrmConversationDto
import com.dscorp.wispadmin.wispadmin.security.PlatformAuthFilter
import com.dscorp.wispadmin.wispadmin.service.whatsapp.CrmConversationConflictException
import com.dscorp.wispadmin.wispadmin.service.whatsapp.CrmConversationService
import com.dscorp.wispadmin.wispadmin.service.whatsapp.WhatsAppAuditService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.mock.web.MockHttpServletRequest
import java.time.LocalDateTime

class CrmConversationControllerTest {

    private val crmConversationService = mockk<CrmConversationService>()
    private val auditService = mockk<WhatsAppAuditService>(relaxed = true)
    private lateinit var controller: CrmConversationController

    @BeforeEach
    fun setUp() {
        controller = CrmConversationController(crmConversationService, auditService)
    }

    @Test
    fun `claim returns conflict when already taken`() {
        val request = MockHttpServletRequest().apply {
            setAttribute(PlatformAuthFilter.AUTH_USER_ID_ATTRIBUTE, 7)
            setAttribute(PlatformAuthFilter.AUTH_USERNAME_ATTRIBUTE, "sec1")
            setAttribute(PlatformAuthFilter.AUTH_USER_TYPE_ATTRIBUTE, "SECRETARY")
        }
        every { crmConversationService.claim(5L, 7, "sec1") } throws
            CrmConversationConflictException("La conversacion ya fue tomada por otro agente")

        val response = controller.claim(5L, request)

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
    }

    @Test
    fun `claim succeeds and audits`() {
        val request = MockHttpServletRequest().apply {
            setAttribute(PlatformAuthFilter.AUTH_USER_ID_ATTRIBUTE, 7)
            setAttribute(PlatformAuthFilter.AUTH_USERNAME_ATTRIBUTE, "sec1")
            setAttribute(PlatformAuthFilter.AUTH_USER_TYPE_ATTRIBUTE, "SECRETARY")
        }
        val dto = CrmConversationDto(
            id = 5L,
            channel = "WHATSAPP",
            phone = "51900001111",
            subscriptionId = null,
            status = "ASSIGNED",
            assignedAgentId = 7,
            assignedAgentName = "Agent One",
            priority = 0,
            claimedAt = LocalDateTime.now(),
            resolvedAt = null,
            lastInboundAt = LocalDateTime.now(),
            lastOutboundAt = null,
            version = 1,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )
        every { crmConversationService.claim(5L, 7, "sec1") } returns dto

        val response = controller.claim(5L, request)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(dto, response.body)
        verify {
            auditService.recordAccess("sec1", "/crm/conversations/5/claim", "phone=51900001111")
        }
    }
}
