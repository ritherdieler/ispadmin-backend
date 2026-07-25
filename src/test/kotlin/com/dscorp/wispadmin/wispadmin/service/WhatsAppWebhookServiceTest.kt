package com.dscorp.wispadmin.wispadmin.service

import com.dscorp.wispadmin.wispadmin.config.WhatsAppProperties
import com.dscorp.wispadmin.wispadmin.data.model.WhatsAppMessageLog
import com.dscorp.wispadmin.wispadmin.repository.WhatsAppMessageLogRepository
import com.dscorp.wispadmin.wispadmin.repository.WhatsAppWebhookEventRepository
import com.dscorp.wispadmin.wispadmin.service.whatsapp.WhatsAppAccountEventService
import com.dscorp.wispadmin.wispadmin.service.whatsapp.WhatsAppServiceWindowService
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class WhatsAppWebhookServiceTest {

    private val whatsAppProperties = WhatsAppProperties().apply {
        webhookVerifyToken = "token"
        appSecret = "secret"
    }
    private val messageLogRepository = mockk<WhatsAppMessageLogRepository>()
    private val webhookEventRepository = mockk<WhatsAppWebhookEventRepository>()
    private val inboundMessageService = mockk<WhatsAppInboundMessageService>(relaxed = true)
    private val accountEventService = mockk<WhatsAppAccountEventService>(relaxed = true)
    private val serviceWindowService = mockk<WhatsAppServiceWindowService>(relaxed = true)

    private lateinit var service: WhatsAppWebhookService

    @BeforeEach
    fun setUp() {
        service = WhatsAppWebhookService(
            whatsAppProperties = whatsAppProperties,
            whatsAppMessageLogRepository = messageLogRepository,
            whatsAppWebhookEventRepository = webhookEventRepository,
            whatsAppInboundMessageService = inboundMessageService,
            accountEventService = accountEventService,
            serviceWindowService = serviceWindowService
        )
    }

    @Test
    fun `processPayload updates delivery timestamps and conversation metadata`() {
        val log = WhatsAppMessageLog(
            id = 1,
            phone = "51902354183",
            status = "SENT",
            metaMessageId = "wamid.abc123"
        )
        every { webhookEventRepository.existsByEventKey(any()) } returns false
        every { webhookEventRepository.save(any()) } answers { firstArg() }
        every { messageLogRepository.findByMetaMessageId("wamid.abc123") } returns log
        val savedSlot = slot<WhatsAppMessageLog>()
        every { messageLogRepository.save(capture(savedSlot)) } answers { firstArg() }

        service.processPayload(
            """
            {
              "object": "whatsapp_business_account",
              "entry": [{
                "changes": [{
                  "field": "messages",
                  "value": {
                    "statuses": [{
                      "id": "wamid.abc123",
                      "status": "delivered",
                      "timestamp": "1700000000",
                      "conversation": { "id": "conv-1", "origin": { "type": "utility" } },
                      "pricing": { "billable": true, "pricing_model": "CBP", "category": "utility" }
                    }]
                  }
                }]
              }]
            }
            """.trimIndent()
        )

        val saved = savedSlot.captured
        assertEquals("delivered", saved.deliveryStatus)
        assertNotNull(saved.deliveredAt)
        assertEquals("conv-1", saved.conversationId)
        assertEquals("utility", saved.conversationCategory)
        assertEquals(true, saved.billable)
        assertEquals("CBP", saved.pricingModel)
    }

    @Test
    fun `processPayload delegates management webhooks to account event service`() {
        service.processPayload(
            """
            {
              "object": "whatsapp_business_account",
              "entry": [{
                "changes": [{
                  "field": "message_template_status_update",
                  "value": {
                    "message_template_name": "payment_reminder_gigaperu",
                    "message_template_id": "123",
                    "event": "PAUSED"
                  }
                }]
              }]
            }
            """.trimIndent()
        )

        verify(exactly = 1) {
            accountEventService.recordManagementEvent(
                "message_template_status_update",
                match { it.path("message_template_name").asText() == "payment_reminder_gigaperu" }
            )
        }
    }
}
