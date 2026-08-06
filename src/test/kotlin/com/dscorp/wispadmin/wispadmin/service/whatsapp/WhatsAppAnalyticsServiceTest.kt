package com.dscorp.wispadmin.wispadmin.service.whatsapp

import com.dscorp.wispadmin.wispadmin.data.model.WhatsAppMessageLog
import com.dscorp.wispadmin.wispadmin.repository.PaymentRepository
import com.dscorp.wispadmin.wispadmin.repository.WhatsAppInboundMessageRepository
import com.dscorp.wispadmin.wispadmin.repository.WhatsAppMessageLogRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import com.dscorp.wispadmin.wispadmin.data.model.Payment
import com.dscorp.wispadmin.wispadmin.data.model.Subscription
import com.dscorp.wispadmin.wispadmin.data.model.EquipmentCondition
import org.mockito.ArgumentMatchers.anyCollection
import org.mockito.ArgumentMatchers.anyList
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class WhatsAppAnalyticsServiceTest {

    private val messageLogRepository = mock(WhatsAppMessageLogRepository::class.java)
    private val inboundMessageRepository = mock(WhatsAppInboundMessageRepository::class.java)
    private val paymentRepository = mock(PaymentRepository::class.java)
    private val metaAnalyticsClient = mock(WhatsAppMetaAnalyticsClient::class.java)
    private val objectMapper = ObjectMapper()
    private val zone = ZoneId.of("America/Lima")
    private val service = WhatsAppAnalyticsService(
        messageLogRepository,
        inboundMessageRepository,
        paymentRepository,
        metaAnalyticsClient
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

    @Test
    fun `campaigns computes estimatedMetaCost and roi deduplicating by conversationId`() {
        val from = LocalDateTime.now().minusDays(7)
        val to = LocalDateTime.now()
        `when`(messageLogRepository.findByCreatedAtBetween(from, to)).thenReturn(
            listOf(
                WhatsAppMessageLog(
                    campaignId = "camp-1",
                    status = "SENT",
                    metaMessageId = "w1",
                    conversationId = "conv-1",
                    conversationCategory = "marketing",
                    billable = true
                ),
                WhatsAppMessageLog(
                    campaignId = "camp-1",
                    status = "SENT",
                    metaMessageId = "w2",
                    conversationId = "conv-1",
                    conversationCategory = "marketing",
                    billable = true
                ),
                WhatsAppMessageLog(
                    campaignId = "camp-1",
                    status = "SENT",
                    metaMessageId = "w3",
                    conversationId = "conv-2",
                    conversationCategory = "marketing",
                    billable = true
                )
            )
        )
        `when`(inboundMessageRepository.findByCreatedAtBetween(from, to)).thenReturn(emptyList())
        `when`(metaAnalyticsClient.fetchConversationAnalytics(from.atZone(zone).toInstant(), to.atZone(zone).toInstant()))
            .thenReturn(
                objectMapper.readTree(
                    """{"data":[{"data_points":[{"conversation_category":"MARKETING","conversation":10,"cost":5.0,"currency":"USD"}]}]}"""
                )
            )

        val campaigns = service.campaigns(from, to)

        assertEquals(1.0, campaigns.first().estimatedMetaCost, 0.0001)
        assertEquals(-1.0, campaigns.first().roi, 0.0001)
    }

    @Test
    fun `campaigns returns zero estimatedMetaCost when Meta conversation analytics has no data`() {
        val from = LocalDateTime.now().minusDays(7)
        val to = LocalDateTime.now()
        `when`(messageLogRepository.findByCreatedAtBetween(from, to)).thenReturn(
            listOf(
                WhatsAppMessageLog(
                    campaignId = "camp-1",
                    status = "SENT",
                    metaMessageId = "w1",
                    conversationId = "conv-1",
                    conversationCategory = "marketing",
                    billable = true
                )
            )
        )
        `when`(inboundMessageRepository.findByCreatedAtBetween(from, to)).thenReturn(emptyList())
        `when`(metaAnalyticsClient.fetchConversationAnalytics(from.atZone(zone).toInstant(), to.atZone(zone).toInstant()))
            .thenReturn(objectMapper.createObjectNode())

        val campaigns = service.campaigns(from, to)

        assertEquals(0.0, campaigns.first().estimatedMetaCost, 0.0001)
        assertEquals(0.0, campaigns.first().roi, 0.0001)
    }

    @Test
    fun `campaignDetail computes estimatedMetaCost using the campaign own date range`() {
        val createdAt = LocalDateTime.now().minusHours(2)
        `when`(messageLogRepository.findByCampaignId("camp-1")).thenReturn(
            listOf(
                WhatsAppMessageLog(
                    campaignId = "camp-1",
                    status = "SENT",
                    metaMessageId = "w1",
                    conversationId = "conv-1",
                    conversationCategory = "utility",
                    billable = true,
                    createdAt = createdAt
                )
            )
        )
        `when`(inboundMessageRepository.findByCreatedAtBetween(createdAt, createdAt.plusSeconds(1)))
            .thenReturn(emptyList())
        `when`(
            metaAnalyticsClient.fetchConversationAnalytics(
                createdAt.atZone(zone).toInstant(),
                createdAt.plusSeconds(1).atZone(zone).toInstant()
            )
        ).thenReturn(
            objectMapper.readTree(
                """{"data":[{"data_points":[{"conversation_category":"UTILITY","conversation":4,"cost":2.0,"currency":"USD"}]}]}"""
            )
        )

        val detail = service.campaignDetail("camp-1")

        assertEquals(0.5, detail?.summary?.estimatedMetaCost ?: -1.0, 0.0001)
    }

    @Test
    fun `overviewSeries groups accepted notDelivered failed by day`() {
        val from = LocalDateTime.of(2026, 8, 1, 0, 0)
        val to = LocalDateTime.of(2026, 8, 3, 0, 0)
        `when`(messageLogRepository.findByCreatedAtBetween(from, to)).thenReturn(
            listOf(
                WhatsAppMessageLog(
                    status = "SENT",
                    metaMessageId = "w1",
                    deliveryStatus = "delivered",
                    deliveredAt = LocalDateTime.of(2026, 8, 1, 9, 5),
                    createdAt = LocalDateTime.of(2026, 8, 1, 9, 0)
                ),
                WhatsAppMessageLog(
                    status = "SENT",
                    metaMessageId = "w2",
                    deliveryStatus = "failed",
                    createdAt = LocalDateTime.of(2026, 8, 1, 10, 0)
                ),
                WhatsAppMessageLog(
                    status = "SENT",
                    metaMessageId = "w3",
                    deliveryStatus = "delivered",
                    deliveredAt = LocalDateTime.of(2026, 8, 2, 9, 5),
                    createdAt = LocalDateTime.of(2026, 8, 2, 9, 0)
                )
            )
        )

        val series = service.overviewSeries(from, to)

        assertEquals(2, series.size)
        assertEquals(LocalDate.of(2026, 8, 1), series[0].date)
        assertEquals(2, series[0].accepted)
        assertEquals(1, series[0].notDelivered)
        assertEquals(1, series[0].failed)
        assertEquals(LocalDate.of(2026, 8, 2), series[1].date)
        assertEquals(1, series[1].accepted)
        assertEquals(0, series[1].notDelivered)
        assertEquals(0, series[1].failed)
    }

    @Test
    fun `overviewSeries filters by templateCode`() {
        val from = LocalDateTime.of(2026, 8, 1, 0, 0)
        val to = LocalDateTime.of(2026, 8, 2, 0, 0)
        `when`(messageLogRepository.findByCreatedAtBetween(from, to)).thenReturn(
            listOf(
                WhatsAppMessageLog(
                    status = "SENT",
                    messageType = "PAYMENT_REMINDER",
                    metaMessageId = "w1",
                    createdAt = LocalDateTime.of(2026, 8, 1, 9, 0)
                ),
                WhatsAppMessageLog(
                    status = "SENT",
                    messageType = "WELCOME_CUSTOMER",
                    metaMessageId = "w2",
                    createdAt = LocalDateTime.of(2026, 8, 1, 10, 0)
                )
            )
        )

        val series = service.overviewSeries(from, to, templateCode = "PAYMENT_REMINDER")

        assertEquals(1, series.size)
        assertEquals(1, series[0].accepted)
    }

    private fun subscription(id: Int) = Subscription(
        firstName = "Juan",
        lastName = "Perez",
        phone = "987654321",
        equipmentCondition = EquipmentCondition.LOAN
    ).apply { this.id = id }

    private fun anyLocalDateTime(): LocalDateTime =
        org.mockito.ArgumentMatchers.any(LocalDateTime::class.java) ?: LocalDateTime.now()

    private fun paidPayment(subscriptionId: Int, amountPaid: Double, paymentDate: LocalDateTime) = Payment(
        discountAmount = 0.0,
        paid = true,
        amountToPay = amountPaid,
        amountPaid = amountPaid,
        paymentDateDatetime = paymentDate,
        subscription = subscription(subscriptionId)
    )

    @Test
    fun `conversion consulta pagos en una sola query batch para varios logs de la misma suscripcion`() {
        val from = LocalDateTime.of(2026, 8, 1, 0, 0)
        val to = LocalDateTime.of(2026, 8, 3, 0, 0)
        `when`(messageLogRepository.findByCreatedAtBetween(from, to)).thenReturn(
            listOf(
                WhatsAppMessageLog(
                    status = "SENT",
                    subscriptionId = 10,
                    metaMessageId = "w1",
                    createdAt = from.plusHours(1)
                ),
                WhatsAppMessageLog(
                    status = "SENT",
                    subscriptionId = 10,
                    metaMessageId = "w2",
                    createdAt = from.plusHours(2)
                ),
                WhatsAppMessageLog(
                    status = "SENT",
                    subscriptionId = 20,
                    metaMessageId = "w3",
                    createdAt = from.plusHours(3)
                )
            )
        )

        service.conversion(from, to)

        verify(paymentRepository, times(1)).findBySubscriptionIdInAndPaidTrueAndPaymentDateDatetimeBetween(
            anyCollection(),
            anyLocalDateTime(),
            anyLocalDateTime()
        )
        verify(paymentRepository, never()).findBySubscriptionIdOrderByBillingDateDatetimeDesc(
            org.mockito.ArgumentMatchers.anyInt()
        )
    }

    @Test
    fun `conversion calcula recoveredAmount a partir de los pagos cargados en batch`() {
        val from = LocalDateTime.of(2026, 8, 1, 0, 0)
        val to = LocalDateTime.of(2026, 8, 3, 0, 0)
        val createdAt = from.plusHours(1)
        `when`(messageLogRepository.findByCreatedAtBetween(from, to)).thenReturn(
            listOf(
                WhatsAppMessageLog(
                    status = "SENT",
                    subscriptionId = 10,
                    metaMessageId = "w1",
                    createdAt = createdAt
                )
            )
        )
        `when`(
            paymentRepository.findBySubscriptionIdInAndPaidTrueAndPaymentDateDatetimeBetween(
                anyCollection(),
                anyLocalDateTime(),
                anyLocalDateTime()
            )
        ).thenReturn(listOf(paidPayment(10, 50.0, createdAt.plusHours(2))))

        val result = service.conversion(from, to)

        assertEquals(1, result.converted)
        assertEquals(50.0, result.recoveredAmount, 0.0001)
    }

    @Test
    fun `campaigns consulta pagos en una sola query batch para todas las campanas`() {
        val from = LocalDateTime.of(2026, 8, 1, 0, 0)
        val to = LocalDateTime.of(2026, 8, 3, 0, 0)
        `when`(messageLogRepository.findByCreatedAtBetween(from, to)).thenReturn(
            listOf(
                WhatsAppMessageLog(
                    campaignId = "camp-1",
                    status = "SENT",
                    subscriptionId = 10,
                    metaMessageId = "w1",
                    createdAt = from.plusHours(1)
                ),
                WhatsAppMessageLog(
                    campaignId = "camp-2",
                    status = "SENT",
                    subscriptionId = 20,
                    metaMessageId = "w2",
                    createdAt = from.plusHours(2)
                )
            )
        )
        `when`(inboundMessageRepository.findByCreatedAtBetween(from, to)).thenReturn(emptyList())

        service.campaigns(from, to)

        verify(paymentRepository, times(1)).findBySubscriptionIdInAndPaidTrueAndPaymentDateDatetimeBetween(
            anyCollection(),
            anyLocalDateTime(),
            anyLocalDateTime()
        )
    }

    @Test
    fun `overview no vuelve a consultar messageLogRepository dentro de conversion`() {
        val from = LocalDateTime.now().minusDays(7)
        val to = LocalDateTime.now()
        `when`(messageLogRepository.findByCreatedAtBetween(from, to)).thenReturn(
            listOf(WhatsAppMessageLog(status = "SENT", subscriptionId = 10, metaMessageId = "w1"))
        )
        `when`(inboundMessageRepository.findByCreatedAtBetween(from, to)).thenReturn(emptyList())

        service.overview(from, to)

        verify(messageLogRepository, times(1)).findByCreatedAtBetween(from, to)
    }
}
