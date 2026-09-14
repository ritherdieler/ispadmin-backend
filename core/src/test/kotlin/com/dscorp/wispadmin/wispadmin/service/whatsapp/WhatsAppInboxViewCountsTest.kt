package com.dscorp.wispadmin.wispadmin.service.whatsapp

import com.dscorp.wispadmin.wispadmin.config.WhatsAppProperties
import com.dscorp.wispadmin.wispadmin.data.model.CrmChannel
import com.dscorp.wispadmin.wispadmin.data.model.CrmConversation
import com.dscorp.wispadmin.wispadmin.data.model.CrmConversationStatus
import com.dscorp.wispadmin.wispadmin.repository.CrmConversationRepository
import com.dscorp.wispadmin.wispadmin.repository.PaymentRepository
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionRepository
import com.dscorp.wispadmin.wispadmin.repository.UserRepository
import com.dscorp.wispadmin.wispadmin.repository.WhatsAppInboundMessageRepository
import com.dscorp.wispadmin.wispadmin.repository.WhatsAppMessageLogRepository
import com.dscorp.wispadmin.wispadmin.repository.WhatsAppSyncedTemplateRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.sql.Timestamp
import java.time.LocalDateTime

class WhatsAppInboxViewCountsTest {

    private val inboundMessageRepository = mockk<WhatsAppInboundMessageRepository>()
    private val messageLogRepository = mockk<WhatsAppMessageLogRepository>(relaxed = true)
    private val subscriptionRepository = mockk<SubscriptionRepository>(relaxed = true)
    private val serviceWindowService = mockk<WhatsAppServiceWindowService>(relaxed = true)
    private val paymentRepository = mockk<PaymentRepository>(relaxed = true)
    private val crmConversationRepository = mockk<CrmConversationRepository>(relaxed = true)
    private val crmTicketLinkService = mockk<CrmTicketLinkService>(relaxed = true)
    private val syncedTemplateRepository = mockk<WhatsAppSyncedTemplateRepository>(relaxed = true)
    private val templateSyncService = mockk<WhatsAppTemplateSyncService>(relaxed = true)
    private val userRepository = mockk<UserRepository>(relaxed = true)

    private lateinit var service: WhatsAppConversationQueryService

    private val now: LocalDateTime = LocalDateTime.of(2026, 9, 1, 12, 0)

    @BeforeEach
    fun setUp() {
        service = WhatsAppConversationQueryService(
            inboundMessageRepository = inboundMessageRepository,
            messageLogRepository = messageLogRepository,
            subscriptionRepository = subscriptionRepository,
            serviceWindowService = serviceWindowService,
            paymentRepository = paymentRepository,
            crmConversationRepository = crmConversationRepository,
            crmTicketLinkService = crmTicketLinkService,
            templateDisplayService = WhatsAppTemplateDisplayService(
                syncedTemplateRepository = syncedTemplateRepository,
                templateSyncService = templateSyncService,
                whatsAppProperties = WhatsAppProperties()
            ),
            operatorDisplayNameResolver = WhatsAppOperatorDisplayNameResolver(userRepository)
        )
        every { crmConversationRepository.findByChannelAndPhoneIn(any(), any()) } returns emptyList()
        every { inboundMessageRepository.countAllUnread() } returns 0
        every { inboundMessageRepository.countPhonesWithUnread() } returns 0
    }

    private fun signals(
        phone: String,
        lastInboundAt: LocalDateTime? = null,
        lastOutboundAt: LocalDateTime? = null,
        latestMediaAt: LocalDateTime? = null,
        latestAdvisorAt: LocalDateTime? = null
    ): Array<Any> = arrayOf(
        phone,
        lastInboundAt?.let { Timestamp.valueOf(it) },
        lastOutboundAt?.let { Timestamp.valueOf(it) },
        latestMediaAt?.let { Timestamp.valueOf(it) },
        latestAdvisorAt?.let { Timestamp.valueOf(it) }
    ) as Array<Any>

    private fun crm(
        phone: String,
        status: CrmConversationStatus,
        assignedAgentId: Int? = null,
        resolvedAt: LocalDateTime? = null
    ) = CrmConversation(
        id = phone.hashCode().toLong(),
        channel = CrmChannel.WHATSAPP,
        phone = phone,
        status = status,
        assignedAgentId = assignedAgentId,
        resolvedAt = resolvedAt
    )

    @Test
    fun `los contadores del inbox se resuelven con una sola consulta agregada`() {
        every { inboundMessageRepository.findRecentActivePhones(any()) } returns listOf("51987000001")
        every { inboundMessageRepository.aggregateInboxSignalsByPhoneIn(any()) } returns listOf(
            signals("51987000001", lastInboundAt = now)
        )

        service.getInboxViewCounts(agentId = 7)

        verify(exactly = 1) { inboundMessageRepository.aggregateInboxSignalsByPhoneIn(any()) }
        verify(exactly = 0) { inboundMessageRepository.findLatestInboundByPhoneIn(any()) }
        verify(exactly = 0) { messageLogRepository.findLatestOutboundByPhoneIn(any()) }
        verify(exactly = 0) { inboundMessageRepository.countUnreadByPhoneIn(any()) }
        verify(exactly = 0) { inboundMessageRepository.findLatestMediaAtByPhoneIn(any()) }
        verify(exactly = 0) { inboundMessageRepository.findLatestAdvisorRequestAtByPhoneIn(any()) }
        verify(exactly = 0) { inboundMessageRepository.findLatestSubscriptionIdByPhoneIn(any()) }
        verify(exactly = 0) { inboundMessageRepository.findLatestButtonReplyIdByPhoneIn(any()) }
        verify(exactly = 0) { subscriptionRepository.findNameProjectionsByIdIn(any()) }
        verify(exactly = 0) { serviceWindowService.getServiceWindows(any()) }
    }

    @Test
    fun `cuenta cola, mios, equipo, comprobantes, asesores y resueltos`() {
        every { inboundMessageRepository.findRecentActivePhones(any()) } returns listOf(
            "51987000001",
            "51987000002",
            "51987000003",
            "51987000004",
            "51987000005",
            "51987000006"
        )
        every { inboundMessageRepository.aggregateInboxSignalsByPhoneIn(any()) } returns listOf(
            signals("51987000001", lastInboundAt = now),
            signals("51987000002", lastInboundAt = now, lastOutboundAt = now.minusMinutes(5)),
            signals("51987000003", lastInboundAt = now, lastOutboundAt = now.minusMinutes(5)),
            signals("51987000004", lastInboundAt = now, latestMediaAt = now),
            signals("51987000005", lastInboundAt = now, latestAdvisorAt = now),
            signals("51987000006", lastInboundAt = now.minusHours(2), lastOutboundAt = now)
        )
        every { crmConversationRepository.findByChannelAndPhoneIn(any(), any()) } returns listOf(
            crm("51987000002", CrmConversationStatus.ASSIGNED, assignedAgentId = 7),
            crm("51987000003", CrmConversationStatus.ASSIGNED, assignedAgentId = 9),
            crm("51987000006", CrmConversationStatus.RESOLVED, resolvedAt = now.minusMinutes(1))
        )
        every { inboundMessageRepository.countAllUnread() } returns 42
        every { inboundMessageRepository.countPhonesWithUnread() } returns 5

        val counts = service.getInboxViewCounts(agentId = 7)

        assertEquals(3L, counts.queue)
        assertEquals(1L, counts.mine)
        assertEquals(1L, counts.team)
        assertEquals(1L, counts.receipts)
        assertEquals(1L, counts.advisors)
        assertEquals(1L, counts.resolved)
        assertEquals(6L, counts.all)
        assertEquals(42L, counts.totalUnread)
        assertEquals(5L, counts.conversationsWithUnread)
    }

    @Test
    fun `las variantes peruanas del mismo numero cuentan como una conversacion`() {
        every { inboundMessageRepository.findRecentActivePhones(any()) } returns listOf(
            "51987000001",
            "987000001"
        )
        every { inboundMessageRepository.aggregateInboxSignalsByPhoneIn(any()) } returns listOf(
            signals("51987000001", lastInboundAt = now.minusHours(1)),
            signals("987000001", lastInboundAt = now)
        )

        val counts = service.getInboxViewCounts(agentId = null)

        assertEquals(1L, counts.all)
        assertEquals(1L, counts.queue)
    }

    @Test
    fun `sin actividad reciente no consulta senales ni devuelve conversaciones`() {
        every { inboundMessageRepository.findRecentActivePhones(any()) } returns emptyList()

        val counts = service.getInboxViewCounts(agentId = 7)

        assertEquals(0L, counts.all)
        assertEquals(0L, counts.queue)
        verify(exactly = 0) { inboundMessageRepository.aggregateInboxSignalsByPhoneIn(any()) }
    }

    @Test
    fun `un comprobante anterior a la resolucion no cuenta como pendiente`() {
        every { inboundMessageRepository.findRecentActivePhones(any()) } returns listOf("51987000004")
        every { inboundMessageRepository.aggregateInboxSignalsByPhoneIn(any()) } returns listOf(
            signals("51987000004", lastInboundAt = now.minusHours(3), latestMediaAt = now.minusHours(3))
        )
        every { crmConversationRepository.findByChannelAndPhoneIn(any(), any()) } returns listOf(
            crm("51987000004", CrmConversationStatus.RESOLVED, resolvedAt = now.minusHours(1))
        )

        val counts = service.getInboxViewCounts(agentId = 7)

        assertEquals(0L, counts.receipts)
        assertEquals(1L, counts.resolved)
    }
}
