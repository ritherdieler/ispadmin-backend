package com.dscorp.wispadmin.wispadmin.service.whatsapp

import com.dscorp.wispadmin.wispadmin.config.CrmLlmProperties
import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.client.RestTemplate

class LlmClientTest {

    private val settingsService = mockk<CrmOpenAiSettingsService>()
    private val restTemplate = mockk<RestTemplate>()
    private val properties = CrmLlmProperties().apply {
        defaultModel = "gpt-4o-mini"
        timeoutMs = 2000
        baseUrl = "https://api.openai.com/v1"
        confidenceThreshold = 0.65
    }
    private lateinit var client: LlmClient

    @BeforeEach
    fun setUp() {
        client = LlmClient(settingsService, properties, ObjectMapper(), restTemplate)
    }

    @Test
    fun `classifyIntent returns null when llm disabled`() {
        every { settingsService.resolveRuntimeConfig() } returns CrmOpenAiRuntimeConfig(
            enabled = false,
            apiKey = null,
            model = "gpt-4o-mini"
        )
        assertNull(client.classifyIntent("hola quiero saber mi deuda"))
        verify(exactly = 0) { restTemplate.exchange(any<String>(), any<HttpMethod>(), any<HttpEntity<*>>(), any<Class<*>>()) }
    }

    @Test
    fun `classifyIntent parses llm json response`() {
        every { settingsService.resolveRuntimeConfig() } returns CrmOpenAiRuntimeConfig(
            enabled = true,
            apiKey = "sk-test",
            model = "gpt-4o-mini"
        )
        stubChatCompletion("""{"intent":"DEBT_INQUIRY","confidence":0.91}""")
        val result = client.classifyIntent("cuanto debo este mes")
        assertEquals(WhatsAppInboundIntent.DEBT_INQUIRY, result?.intent)
        assertEquals(0.91, result?.confidence ?: 0.0, 0.001)
    }

    @Test
    fun `testConnection fails when not configured`() {
        every { settingsService.resolveRuntimeConfig() } returns CrmOpenAiRuntimeConfig(
            enabled = false,
            apiKey = null,
            model = "gpt-4o-mini"
        )
        val result = client.testConnection()
        assertFalse(result.success)
        assertTrue(result.message.contains("configur", ignoreCase = true) || result.message.contains("deshabil", ignoreCase = true))
    }

    @Test
    fun `summarizeHandoff returns fallback without llm`() {
        every { settingsService.resolveRuntimeConfig() } returns CrmOpenAiRuntimeConfig(
            enabled = false,
            apiKey = null,
            model = "gpt-4o-mini"
        )
        val summary = client.summarizeHandoff(
            phone = "51999",
            reason = "human_escalation",
            recentMessages = listOf("Cliente: hola", "Bot: menu", "Cliente: quiero asesor")
        )
        assertTrue(summary.contains("human_escalation"))
        assertTrue(summary.contains("quiero asesor"))
    }

    @Test
    fun `suggestReply returns null when disabled`() {
        every { settingsService.resolveRuntimeConfig() } returns CrmOpenAiRuntimeConfig(
            enabled = false,
            apiKey = null,
            model = "gpt-4o-mini"
        )
        assertNull(client.suggestReply(listOf("Cliente: hola"), "sin deuda"))
    }

    private fun stubChatCompletion(content: String) {
        val body = """
            {"choices":[{"message":{"content":${ObjectMapper().writeValueAsString(content)}}}]}
        """.trimIndent()
        every {
            restTemplate.exchange(any<String>(), eq(HttpMethod.POST), any<HttpEntity<*>>(), eq(String::class.java))
        } returns ResponseEntity(body, HttpStatus.OK)
    }
}
