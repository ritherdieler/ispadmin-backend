package com.dscorp.wispadmin.wispadmin.service.whatsapp

import com.dscorp.wispadmin.wispadmin.data.model.WhatsAppMessageLog
import com.dscorp.wispadmin.wispadmin.repository.PaymentRepository
import com.dscorp.wispadmin.wispadmin.repository.WhatsAppInboundMessageRepository
import com.dscorp.wispadmin.wispadmin.repository.WhatsAppMessageLogRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.time.LocalDateTime

class WhatsAppAnalyticsServiceTest {

    private val messageLogRepository = mock(WhatsAppMessageLogRepository::class.java)
    private val inboundMessageRepository = mock(WhatsAppInboundMessageRepository::class.java)
    private val paymentRepository = mock(PaymentRepository::class.java)
    private val service = WhatsAppAnalyticsService(
        messageLogRepository,
        inboundMessageRepository,
        paymentRepository
    )

    @Test
    fun `overview counts funnel metrics`() {
        val from = LocalDateTime.now().minusDays(7)
        val to = LocalDateTime.now()
        `when`(messageLogRepository.findByCreatedAtBetween(from, to)).thenReturn(
            listOf(
                WhatsAppMessageLog(status = "SENT", metaMessageId = "w1", deliveredAt = LocalDateTime.now()),
                WhatsAppMessageLog(status = "SENT", metaMessageId = null, readAt = LocalDateTime.now()),
                WhatsAppMessageLog(status = "FAILED")
            )
        )
        `when`(inboundMessageRepository.findByCreatedAtBetween(from, to)).thenReturn(emptyList())

        val overview = service.overview(from, to)
        assertEquals(2, overview.sent)
        assertEquals(1, overview.delivered)
        assertEquals(1, overview.read)
        assertEquals(1, overview.sentWithoutMetaMessageId)
        assertEquals(7, overview.periodDays)
    }

    @Test
    fun `overview filters by template code`() {
        val from = LocalDateTime.now().minusDays(7)
        val to = LocalDateTime.now()
        `when`(messageLogRepository.findByCreatedAtBetween(from, to)).thenReturn(
            listOf(
                WhatsAppMessageLog(status = "SENT", messageType = "PAYMENT_REMINDER", metaMessageId = "w1"),
                WhatsAppMessageLog(status = "SENT", messageType = "WELCOME_CUSTOMER", metaMessageId = "w2")
            )
        )
        `when`(inboundMessageRepository.findByCreatedAtBetween(from, to)).thenReturn(emptyList())

        val overview = service.overview(from, to, templateCode = "PAYMENT_REMINDER")
        assertEquals(1, overview.sent)
    }

    @Test
    fun `overview separates accepted API sends from Meta confirmed statuses`() {
        val from = LocalDateTime.now().minusDays(1)
        val to = LocalDateTime.now()
        `when`(messageLogRepository.findByCreatedAtBetween(from, to)).thenReturn(
            listOf(
                WhatsAppMessageLog(status = "SENT", metaMessageId = "w1"),
                WhatsAppMessageLog(status = "SENT", metaMessageId = "w2", deliveryStatus = "delivered"),
                WhatsAppMessageLog(status = "SENT", metaMessageId = "w3", deliveryStatus = "failed"),
                WhatsAppMessageLog(status = "FAILED")
            )
        )
        `when`(inboundMessageRepository.findByCreatedAtBetween(from, to)).thenReturn(emptyList())

        val overview = service.overview(from, to)

        assertEquals(3, overview.accepted)
        assertEquals(3, overview.sent)
        assertEquals(2, overview.confirmed)
    }

    @Test
    fun `campaigns groups by campaignId and aligns accepted confirmed failed with overview semantics`() {
        val from = LocalDateTime.now().minusDays(7)
        val to = LocalDateTime.now()
        `when`(messageLogRepository.findByCreatedAtBetween(from, to)).thenReturn(
            listOf(
                WhatsAppMessageLog(
                    campaignId = "camp-1",
                    status = "SENT",
                    metaMessageId = "w1",
                    deliveryStatus = "delivered",
                    deliveredAt = LocalDateTime.now(),
                    createdAt = from.plusHours(1)
                ),
                WhatsAppMessageLog(
                    campaignId = "camp-1",
                    status = "SENT",
                    metaMessageId = "w2",
                    deliveryStatus = "failed",
                    createdAt = from.plusHours(2)
                ),
                WhatsAppMessageLog(
                    campaignId = "camp-1",
                    status = "FAILED",
                    createdAt = from.plusHours(3)
                )
            )
        )
        `when`(inboundMessageRepository.findByCreatedAtBetween(from, to)).thenReturn(emptyList())

        val campaigns = service.campaigns(from, to)

        assertEquals(1, campaigns.size)
        val campaign = campaigns.first()
        assertEquals(2, campaign.accepted)
        assertEquals(2, campaign.confirmed)
        assertEquals(1, campaign.failed)
        assertEquals(1, campaign.delivered)
    }

    @Test
    fun `campaigns filters by templateCode`() {
        val from = LocalDateTime.now().minusDays(7)
        val to = LocalDateTime.now()
        `when`(messageLogRepository.findByCreatedAtBetween(from, to)).thenReturn(
            listOf(
                WhatsAppMessageLog(
                    campaignId = "camp-1",
                    messageType = "PAYMENT_REMINDER",
                    status = "SENT",
                    metaMessageId = "w1"
                ),
                WhatsAppMessageLog(
                    campaignId = "camp-2",
                    messageType = "WELCOME_CUSTOMER",
                    status = "SENT",
                    metaMessageId = "w2"
                )
            )
        )
        `when`(inboundMessageRepository.findByCreatedAtBetween(from, to)).thenReturn(emptyList())

        val campaigns = service.campaigns(from, to, templateCode = "PAYMENT_REMINDER")

        assertEquals(1, campaigns.size)
        assertEquals("camp-1", campaigns.first().campaignId)
    }

    @Test
    fun `campaigns filters by operatorUsername`() {
        val from = LocalDateTime.now().minusDays(7)
        val to = LocalDateTime.now()
        `when`(messageLogRepository.findByCreatedAtBetween(from, to)).thenReturn(
            listOf(
                WhatsAppMessageLog(
                    campaignId = "camp-1",
                    operatorUsername = "operator.a",
                    status = "SENT",
                    metaMessageId = "w1"
                ),
                WhatsAppMessageLog(
                    campaignId = "camp-2",
                    operatorUsername = "operator.b",
                    status = "SENT",
                    metaMessageId = "w2"
                )
            )
        )
        `when`(inboundMessageRepository.findByCreatedAtBetween(from, to)).thenReturn(emptyList())

        val campaigns = service.campaigns(from, to, operatorUsername = "operator.b")

        assertEquals(1, campaigns.size)
        assertEquals("camp-2", campaigns.first().campaignId)
    }

    @Test
    fun `campaignDetail returns summary and messages for a campaign`() {
        val createdAt = LocalDateTime.now().minusHours(2)
        `when`(messageLogRepository.findByCampaignId("camp-1")).thenReturn(
            listOf(
                WhatsAppMessageLog(
                    campaignId = "camp-1",
                    status = "SENT",
                    metaMessageId = "w1",
                    deliveredAt = LocalDateTime.now(),
                    createdAt = createdAt
                )
            )
        )
        `when`(inboundMessageRepository.findByCreatedAtBetween(createdAt, createdAt.plusSeconds(1)))
            .thenReturn(emptyList())

        val detail = service.campaignDetail("camp-1")

        assertEquals("camp-1", detail?.summary?.campaignId)
        assertEquals(1, detail?.summary?.accepted)
        assertEquals(1, detail?.messages?.size)
    }

    @Test
    fun `campaignDetail returns null when campaign has no logs`() {
        `when`(messageLogRepository.findByCampaignId("missing")).thenReturn(emptyList())

        val detail = service.campaignDetail("missing")

        assertEquals(null, detail)
    }

    @Test
    fun `campaignDetail filters by templateCode and operatorUsername`() {
        val createdAt = LocalDateTime.now().minusHours(2)
        `when`(messageLogRepository.findByCampaignId("camp-1")).thenReturn(
            listOf(
                WhatsAppMessageLog(
                    campaignId = "camp-1",
                    messageType = "PAYMENT_REMINDER",
                    operatorUsername = "operator.a",
                    status = "SENT",
                    metaMessageId = "w1",
                    createdAt = createdAt
                ),
                WhatsAppMessageLog(
                    campaignId = "camp-1",
                    messageType = "WELCOME_CUSTOMER",
                    operatorUsername = "operator.b",
                    status = "SENT",
                    metaMessageId = "w2",
                    createdAt = createdAt
                )
            )
        )
        `when`(inboundMessageRepository.findByCreatedAtBetween(createdAt, createdAt.plusSeconds(1)))
            .thenReturn(emptyList())

        val detail = service.campaignDetail("camp-1", templateCode = "PAYMENT_REMINDER")

        assertEquals(1, detail?.messages?.size)
        assertEquals("PAYMENT_REMINDER", detail?.summary?.templateCode)

        val filteredByOperator = service.campaignDetail("camp-1", operatorUsername = "operator.b")
        assertEquals(1, filteredByOperator?.messages?.size)
        assertEquals("operator.b", filteredByOperator?.summary?.operatorUsername)
    }
}
