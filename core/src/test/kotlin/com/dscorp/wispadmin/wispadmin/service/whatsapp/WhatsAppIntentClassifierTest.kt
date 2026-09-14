package com.dscorp.wispadmin.wispadmin.service.whatsapp

import com.dscorp.wispadmin.wispadmin.config.CrmLlmProperties
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class WhatsAppIntentClassifierTest {

    private val rulesRouter = WhatsAppInboundIntentRouter()
    private val llmClient = mockk<LlmClient>()
    private val chatStateService = mockk<WhatsAppChatStateService>(relaxed = true)
    private val auditService = mockk<WhatsAppAuditService>(relaxed = true)
    private val properties = CrmLlmProperties().apply {
        confidenceThreshold = 0.65
        maxUnknownRetries = 2
    }
    private lateinit var classifier: WhatsAppIntentClassifier

    @BeforeEach
    fun setUp() {
        classifier = WhatsAppIntentClassifier(
            rulesRouter = rulesRouter,
            llmClient = llmClient,
            chatStateService = chatStateService,
            auditService = auditService,
            properties = properties
        )
        every { chatStateService.getUnknownRetryCount(any()) } returns 0
    }

    @Test
    fun `uses rules for clear keyword without calling llm`() {
        val result = classifier.classify("51999", "cuanto debo")
        assertEquals(WhatsAppInboundIntent.DEBT_INQUIRY, result.intent)
        assertEquals("RULES", result.source)
        assertTrue(result.confidence >= 0.9)
        verify(exactly = 0) { llmClient.classifyIntent(any()) }
    }

    @Test
    fun `uses llm when rules unknown and confidence high`() {
        every { llmClient.classifyIntent("xyzabc no keywords 12345") } returns LlmIntentResult(
            intent = WhatsAppInboundIntent.DEBT_INQUIRY,
            confidence = 0.88
        )
        val result = classifier.classify("51999", "xyzabc no keywords 12345")
        assertEquals(WhatsAppInboundIntent.DEBT_INQUIRY, result.intent)
        assertEquals("LLM", result.source)
    }

    @Test
    fun `falls back to rules when llm confidence below threshold`() {
        every { llmClient.classifyIntent(any()) } returns LlmIntentResult(
            intent = WhatsAppInboundIntent.SUPPORT,
            confidence = 0.2
        )
        val result = classifier.classify("51999", "zzzzqwerty123")
        assertEquals(WhatsAppInboundIntent.UNKNOWN, result.intent)
        assertEquals("FALLBACK", result.source)
    }

    @Test
    fun `escalates after max unknown retries`() {
        every { llmClient.classifyIntent(any()) } returns null
        every { chatStateService.incrementUnknownRetryCount("51999") } returns 2
        val result = classifier.classify("51999", "zzzzqwerty123")
        assertEquals(WhatsAppInboundIntent.HUMAN_ESCALATION, result.intent)
        assertTrue(result.escalate)
        assertEquals("low_confidence_retries", result.escalateReason)
    }

    @Test
    fun `frustration from rules escalates`() {
        val result = classifier.classify("51999", "pesimo servicio")
        assertEquals(WhatsAppInboundIntent.HUMAN_ESCALATION, result.intent)
        assertTrue(result.escalate)
        assertFalse(result.source == "LLM")
    }
}
