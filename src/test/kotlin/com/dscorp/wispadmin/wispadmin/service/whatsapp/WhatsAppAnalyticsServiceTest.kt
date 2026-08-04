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
}
