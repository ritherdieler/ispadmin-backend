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

    @Test
    fun `parseConversationAnalytics reads data_points when data is an object`() {
        val root = objectMapper.readTree(
            """
            {
              "conversation_analytics": {
                "data": {
                  "data_points": [{
                    "conversation": 5250,
                    "cost": 45.05,
                    "currency": "USD"
                  }]
                }
              }
            }
            """.trimIndent()
        )

        val parsed = WhatsAppMetaAnalyticsParser.parseConversationAnalytics(root)

        assertEquals(1, parsed.categories.size)
        assertEquals("UNKNOWN", parsed.categories.first().category)
        assertEquals(5250, parsed.categories.first().conversationCount)
        assertEquals(45.05, parsed.categories.first().cost)
    }

    @Test
    fun `parsePricingAnalytics maps pricing_category tier and pricing_type from Meta`() {
        val root = objectMapper.readTree(
            """
            {
              "pricing_analytics": {
                "data": [{
                  "data_points": [{
                    "country": "PE",
                    "tier": "0:750000",
                    "pricing_type": "REGULAR",
                    "pricing_category": "UTILITY",
                    "volume": 69,
                    "cost": 7.95,
                    "currency": "USD"
                  }, {
                    "country": "PE",
                    "pricing_type": "FREE_CUSTOMER_SERVICE",
                    "pricing_category": "SERVICE",
                    "volume": 50,
                    "cost": 0.2
                  }]
                }]
              }
            }
            """.trimIndent()
        )

        val parsed = WhatsAppMetaAnalyticsParser.parsePricingAnalytics(root)

        assertEquals(2, parsed.tiers.size)
        assertEquals("0:750000", parsed.tiers[0].tier)
        assertEquals("UTILITY", parsed.tiers[0].category)
        assertEquals(69, parsed.tiers[0].volume)
        assertEquals("FREE_CUSTOMER_SERVICE", parsed.tiers[1].tier)
        assertEquals("SERVICE", parsed.tiers[1].category)
    }

    @Test
    fun `parseTemplateAnalytics reads template_analytics edge response`() {
        every { templateRepository.findById("2632273056924580") } returns Optional.of(
            WhatsAppSyncedTemplate(metaTemplateId = "2632273056924580", name = "payment_reminder_gigaperu")
        )

        val root = objectMapper.readTree(
            """
            {
              "data": [{
                "granularity": "DAILY",
                "data_points": [{
                  "template_id": "2632273056924580",
                  "sent": 12,
                  "delivered": 10,
                  "read": 8,
                  "clicked": [{"count": 3}, {"count": 2}]
                }]
              }]
            }
            """.trimIndent()
        )

        val items = WhatsAppMetaAnalyticsParser.parseTemplateAnalytics(root, templateRepository)

        assertEquals(1, items.size)
        assertEquals("payment_reminder_gigaperu", items.first().templateName)
        assertEquals(12, items.first().sent)
        assertEquals(5, items.first().clicked)
    }
}
