package com.dscorp.wispadmin.wispadmin.controller

import com.dscorp.wispadmin.wispadmin.dto.CrmOpenAiSettingsDto
import com.dscorp.wispadmin.wispadmin.dto.CrmOpenAiSettingsUpdateBody
import com.dscorp.wispadmin.wispadmin.dto.CrmOpenAiTestResultDto
import com.dscorp.wispadmin.wispadmin.security.PlatformAuthFilter
import com.dscorp.wispadmin.wispadmin.service.whatsapp.CrmOpenAiSettingsService
import com.dscorp.wispadmin.wispadmin.service.whatsapp.LlmClient
import com.dscorp.wispadmin.wispadmin.service.whatsapp.WhatsAppAuditService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.mock.web.MockHttpServletRequest

class CrmOpenAiSettingsControllerTest {

    private val settingsService = mockk<CrmOpenAiSettingsService>()
    private val llmClient = mockk<LlmClient>()
    private val auditService = mockk<WhatsAppAuditService>(relaxed = true)
    private lateinit var controller: CrmOpenAiSettingsController

    @BeforeEach
    fun setUp() {
        controller = CrmOpenAiSettingsController(settingsService, llmClient, auditService)
    }

    @Test
    fun `get returns settings for ADMIN`() {
        every { settingsService.getSettings() } returns CrmOpenAiSettingsDto(
            configured = true,
            maskedApiKey = "sk-...abcd",
            model = "gpt-4o-mini",
            enabled = true,
            updatedAt = null,
            updatedBy = "admin"
        )
        val response = controller.get(adminRequest())
        assertEquals(HttpStatus.OK, response.statusCode)
        val body = response.body as CrmOpenAiSettingsDto
        assertEquals("sk-...abcd", body.maskedApiKey)
        assertFalse(body.toString().contains("sk-proj"))
    }

    @Test
    fun `get forbidden for SECRETARY`() {
        val response = controller.get(secretaryRequest())
        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
    }

    @Test
    fun `put updates and audits without logging secret`() {
        every {
            settingsService.updateSettings("sk-new-secret-xxxx", "gpt-4o", true, "admin")
        } returns CrmOpenAiSettingsDto(
            configured = true,
            maskedApiKey = "sk-...xxxx",
            model = "gpt-4o",
            enabled = true,
            updatedAt = null,
            updatedBy = "admin"
        )
        val response = controller.put(
            CrmOpenAiSettingsUpdateBody(apiKey = "sk-new-secret-xxxx", model = "gpt-4o", enabled = true),
            adminRequest()
        )
        assertEquals(HttpStatus.OK, response.statusCode)
        verify {
            auditService.recordAccess(
                "admin",
                "/crm/settings/openai",
                match { details -> details != null && !details.contains("sk-new-secret") }
            )
        }
    }

    @Test
    fun `test connection forbidden for SECRETARY`() {
        val response = controller.test(secretaryRequest())
        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
    }

    @Test
    fun `test connection returns llm result for ADMIN`() {
        every { llmClient.testConnection() } returns CrmOpenAiTestResultDto(
            success = true,
            message = "ok",
            model = "gpt-4o-mini"
        )
        val response = controller.test(adminRequest())
        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(true, (response.body as CrmOpenAiTestResultDto).success)
    }

    private fun adminRequest() = MockHttpServletRequest().apply {
        setAttribute(PlatformAuthFilter.AUTH_USER_ID_ATTRIBUTE, 1)
        setAttribute(PlatformAuthFilter.AUTH_USERNAME_ATTRIBUTE, "admin")
        setAttribute(PlatformAuthFilter.AUTH_USER_TYPE_ATTRIBUTE, "ADMIN")
    }

    private fun secretaryRequest() = MockHttpServletRequest().apply {
        setAttribute(PlatformAuthFilter.AUTH_USER_ID_ATTRIBUTE, 2)
        setAttribute(PlatformAuthFilter.AUTH_USERNAME_ATTRIBUTE, "sec")
        setAttribute(PlatformAuthFilter.AUTH_USER_TYPE_ATTRIBUTE, "SECRETARY")
    }
}
