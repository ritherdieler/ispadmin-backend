package com.dscorp.wispadmin.wispadmin.service.whatsapp

import com.dscorp.wispadmin.wispadmin.data.model.WhatsAppSyncedTemplate
import com.dscorp.wispadmin.wispadmin.repository.WhatsAppSyncedTemplateRepository
import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.Optional

class WhatsAppMetaAnalyticsParserTest {

    private val templateRepository = mockk<WhatsAppSyncedTemplateRepository>()
    private val objectMapper = ObjectMapper()

    @Test
    fun `parseTemplateAnalytics aggregates sent delivered read and clicked`() {
        every { templateRepository.findById("123") } returns Optional.of(
            WhatsAppSyncedTemplate(metaTemplateId = "123", name = "payment_reminder_gigaperu")
        )

        val root = objectMapper.readTree(
            """
            {
              "data": [{
                "data_points": [{
                  "template_id": "123",
                  "sent": 10,
                  "delivered": 8,
                  "read": 5,
                  "clicked": [{"count": 2}, {"count": 1}]
                }]
              }]
            }
            """.trimIndent()
        )

        val items = WhatsAppMetaAnalyticsParser.parseTemplateAnalytics(root, templateRepository)
        assertEquals(1, items.size)
        assertEquals("123", items.first().templateId)
        assertEquals("payment_reminder_gigaperu", items.first().templateName)
        assertEquals(10, items.first().sent)
        assertEquals(8, items.first().delivered)
        assertEquals(5, items.first().read)
        assertEquals(3, items.first().clicked)
    }

    @Test
    fun `parseConversationAnalytics maps categories and total cost`() {
        val root = objectMapper.readTree(
            """
            {
              "conversation_analytics": {
                "data": [{
                  "data_points": [{
                    "conversation_category": "UTILITY",
                    "conversation": 4,
                    "cost": 1.5,
                    "currency": "USD"
                  }]
                }]
              }
            }
            """.trimIndent()
        )

        val parsed = WhatsAppMetaAnalyticsParser.parseConversationAnalytics(root)
        assertEquals(1, parsed.categories.size)
        assertEquals("UTILITY", parsed.categories.first().category)
        assertEquals(4, parsed.categories.first().conversationCount)
        assertEquals(1.5, parsed.categories.first().cost)
        assertEquals(1.5, parsed.totalCost)
    }
}
