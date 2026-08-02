package com.dscorp.wispadmin.wispadmin.service.whatsapp

import com.dscorp.wispadmin.wispadmin.data.model.EquipmentCondition
import com.dscorp.wispadmin.wispadmin.data.model.Payment
import com.dscorp.wispadmin.wispadmin.data.model.ServiceStatus
import com.dscorp.wispadmin.wispadmin.data.model.Subscription
import com.dscorp.wispadmin.wispadmin.data.model.WhatsAppInboundMessage
import com.dscorp.wispadmin.wispadmin.data.model.WhatsAppMessageLog
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionRepository
import com.dscorp.wispadmin.wispadmin.repository.WhatsAppInboundMessageRepository
import com.dscorp.wispadmin.wispadmin.repository.WhatsAppMessageLogRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.data.domain.Pageable
import java.time.LocalDateTime
import java.util.Optional

class WhatsAppConversationQueryServiceTest {

    private val inboundMessageRepository = mockk<WhatsAppInboundMessageRepository>()
    private val messageLogRepository = mockk<WhatsAppMessageLogRepository>()
    private val subscriptionRepository = mockk<SubscriptionRepository>()
    private val serviceWindowService = mockk<WhatsAppServiceWindowService>()
    private val paymentRepository = mockk<com.dscorp.wispadmin.wispadmin.repository.PaymentRepository>(relaxed = true)
    private val crmConversationRepository = mockk<com.dscorp.wispadmin.wispadmin.repository.CrmConversationRepository>(relaxed = true)
    private val crmTicketLinkService = mockk<CrmTicketLinkService>(relaxed = true)

    private lateinit var service: WhatsAppConversationQueryService

    @BeforeEach
    fun setUp() {
        service = WhatsAppConversationQueryService(
            inboundMessageRepository = inboundMessageRepository,
            messageLogRepository = messageLogRepository,
            subscriptionRepository = subscriptionRepository,
            serviceWindowService = serviceWindowService,
            paymentRepository = paymentRepository,
            crmConversationRepository = crmConversationRepository,
            crmTicketLinkService = crmTicketLinkService
        )
        every { crmTicketLinkService.listTicketsForPhone(any()) } returns emptyList()
    }

    @Test
    fun `listConversations groups by phone with unread and last preview`() {
        val now = LocalDateTime.of(2026, 7, 25, 10, 0)
        every { inboundMessageRepository.findTop500ByOrderByCreatedAtDesc() } returns listOf(
            WhatsAppInboundMessage(
                id = 1,
                metaMessageId = "in-1",
                phone = "51911111111",
                messageText = "Hola",
                subscriptionId = 10,
                createdAt = now.minusMinutes(30),
                readAt = null
            ),
            WhatsAppInboundMessage(
                id = 2,
                metaMessageId = "in-2",
                phone = "51911111111",
                messageText = null,
                messageType = "button_reply",
                buttonReplyId = "soporte",
                buttonReplyTitle = "Soporte",
                subscriptionId = 10,
                createdAt = now.minusMinutes(10),
                readAt = null
            ),
            WhatsAppInboundMessage(
                id = 3,
                metaMessageId = "in-3",
                phone = "51922222222",
                messageText = "Otro",
                subscriptionId = null,
                mediaStoredPath = "/tmp/receipt.jpg",
                createdAt = now.minusMinutes(5),
                readAt = now
            )
        )
        every { messageLogRepository.findTop500ByOrderByCreatedAtDesc() } returns listOf(
            WhatsAppMessageLog(
                id = 100,
                phone = "51911111111",
                message = "Auto reply",
                messageType = "AUTO_REPLY",
                status = "SENT",
                createdAt = now.minusMinutes(9)
            )
        )
        every { subscriptionRepository.findAllById(listOf(10)) } returns listOf(
            Subscription(
                id = 10,
                firstName = "Ana",
                lastName = "Lopez",
                phone = "911111111",
                serviceStatus = ServiceStatus.ACTIVE,
                equipmentCondition = EquipmentCondition.LOAN
            )
        )
        every {
            serviceWindowService.getServiceWindows(match { it.containsAll(listOf("51911111111", "51922222222")) })
        } returns mapOf(
            "51911111111" to WhatsAppServiceWindowService.WhatsAppServiceWindowStatus(
                phone = "51911111111",
                open = true,
                expiresAt = now.plusHours(20)
            ),
            "51922222222" to WhatsAppServiceWindowService.WhatsAppServiceWindowStatus(
                phone = "51922222222",
                open = false,
                expiresAt = null
            )
        )

        val result = service.listConversations(WhatsAppConversationFilter(limit = 50))

        verify(exactly = 1) { serviceWindowService.getServiceWindows(any()) }
        verify(exactly = 0) { serviceWindowService.getServiceWindow(any()) }
        assertEquals(2, result.size)
        assertEquals("51922222222", result[0].phone)
        assertEquals("Otro", result[0].lastMessagePreview)
        assertEquals(0, result[0].unreadCount)
        assertFalse(result[0].identified)
        assertTrue(result[0].lastHasMedia)
        assertEquals(null, result[0].lastButtonReplyId)

        assertEquals("51911111111", result[1].phone)
        assertEquals("Auto reply", result[1].lastMessagePreview)
        assertEquals(2, result[1].unreadCount)
        assertEquals("Ana Lopez", result[1].clientName)
        assertTrue(result[1].identified)
        assertTrue(result[1].serviceWindowActive)
        assertEquals("soporte", result[1].lastButtonReplyId)
        assertFalse(result[1].lastHasMedia)
    }

    @Test
    fun `getThread merges inbound and outbound sorted by createdAt`() {
        val phone = "51902354183"
        val t1 = LocalDateTime.of(2026, 7, 25, 9, 0)
        val t2 = LocalDateTime.of(2026, 7, 25, 9, 5)
        val t3 = LocalDateTime.of(2026, 7, 25, 9, 10)

        every {
            inboundMessageRepository.findByPhoneOrderByCreatedAtDesc(phone, any<Pageable>())
        } returns listOf(
            WhatsAppInboundMessage(
                id = 43,
                metaMessageId = "in-43",
                phone = phone,
                messageText = null,
                messageType = "button_reply",
                buttonReplyTitle = "Ver deuda",
                createdAt = t3
            ),
            WhatsAppInboundMessage(
                id = 42,
                metaMessageId = "in-42",
                phone = phone,
                messageText = "Hola",
                messageType = "text",
                createdAt = t1
            )
        )
        every {
            messageLogRepository.findByPhoneOrderByCreatedAtDesc(phone, any<Pageable>())
        } returns listOf(
            WhatsAppMessageLog(
                id = 17,
                phone = phone,
                message = "Seleccione una opcion",
                messageType = "AUTO_REPLY",
                status = "SENT",
                deliveryStatus = "delivered",
                operatorUsername = null,
                createdAt = t2
            )
        )

        val thread = service.getThread(phone)

        assertEquals(3, thread.size)
        assertEquals("inbound:42", thread[0].id)
        assertEquals("INBOUND", thread[0].direction)
        assertEquals(42, thread[0].mediaId)
        assertEquals("outbound:17", thread[1].id)
        assertEquals("OUTBOUND", thread[1].direction)
        assertEquals("AUTO_REPLY", thread[1].messageType)
        assertEquals(null, thread[1].templateCode)
        assertEquals("delivered", thread[1].deliveryStatus)
        assertEquals("inbound:43", thread[2].id)
        assertEquals("Ver deuda", thread[2].buttonReplyTitle)
        verify(exactly = 0) { inboundMessageRepository.findByPhoneOrderByCreatedAtAsc(phone) }
        verify(exactly = 0) { messageLogRepository.findByPhoneOrderByCreatedAtAsc(phone) }
    }

    @Test
    fun `getThread maps outbound templateCode from messageType`() {
        val phone = "51902354183"
        every {
            inboundMessageRepository.findByPhoneOrderByCreatedAtDesc(phone, any<Pageable>())
        } returns emptyList()
        every {
            messageLogRepository.findByPhoneOrderByCreatedAtDesc(phone, any<Pageable>())
        } returns listOf(
            WhatsAppMessageLog(
                id = 8,
                phone = phone,
                message = "Recordatorio",
                messageType = "PAYMENT_REMINDER",
                status = "SENT",
                createdAt = LocalDateTime.of(2026, 7, 25, 9, 0)
            )
        )

        val thread = service.getThread(phone)

        assertEquals(1, thread.size)
        assertEquals("PAYMENT_REMINDER", thread[0].templateCode)
        assertEquals("SENT", thread[0].deliveryStatus)
    }

    @Test
    fun `getThread usa pageable con el limit solicitado`() {
        val phone = "51902354183"
        val pageableSlot = io.mockk.slot<Pageable>()
        every {
            inboundMessageRepository.findByPhoneOrderByCreatedAtDesc(phone, capture(pageableSlot))
        } returns emptyList()
        every {
            messageLogRepository.findByPhoneOrderByCreatedAtDesc(phone, any<Pageable>())
        } returns emptyList()

        service.getThread(phone, limit = 25)

        assertEquals(25, pageableSlot.captured.pageSize)
        assertEquals(0, pageableSlot.captured.pageNumber)
    }

    @Test
    fun `getContext returns subscription debt and recent logs`() {
        val phone = "51902354183"
        val subscription = Subscription(
            id = 7,
            firstName = "Luis",
            lastName = "Perez",
            phone = "902354183",
            serviceStatus = ServiceStatus.ACTIVE,
            equipmentCondition = EquipmentCondition.LOAN
        )
        subscription.payments = mutableSetOf(
            Payment(discountAmount = 0.0, paid = false, amountToPay = 40.0, billingDateDatetime = LocalDateTime.now()),
            Payment(discountAmount = 0.0, paid = false, amountToPay = 35.0, billingDateDatetime = LocalDateTime.now()),
            Payment(discountAmount = 0.0, paid = true, amountToPay = 10.0, billingDateDatetime = LocalDateTime.now())
        )

        every { inboundMessageRepository.findTop1ByPhoneOrderByCreatedAtDesc(phone) } returns listOf(
            WhatsAppInboundMessage(
                id = 1,
                metaMessageId = "in-1",
                phone = phone,
                subscriptionId = 7,
                createdAt = LocalDateTime.now()
            )
        )
        every { subscriptionRepository.findById(7) } returns Optional.of(subscription)
        every { subscriptionRepository.findByNormalizedPhone("902354183") } returns listOf(subscription)
        every { serviceWindowService.getServiceWindow(phone) } returns
            WhatsAppServiceWindowService.WhatsAppServiceWindowStatus(
                phone = phone,
                open = true,
                expiresAt = LocalDateTime.now().plusHours(5)
            )
        every { messageLogRepository.findTop10ByPhoneOrderByCreatedAtDesc(phone) } returns listOf(
            WhatsAppMessageLog(
                id = 9,
                phone = phone,
                messageType = "PAYMENT_REMINDER",
                status = "SENT",
                createdAt = LocalDateTime.now()
            )
        )
        every { paymentRepository.findBySubscriptionIdOrderByBillingDateDatetimeDesc(7) } returns emptyList()
        every { crmConversationRepository.findBySubscriptionIdOrderByLastInboundAtDesc(7) } returns emptyList()
        every { crmConversationRepository.findByPhoneAndChannel(any(), any()) } returns null

        val context = service.getContext(phone)

        assertEquals(phone, context.phone)
        assertEquals(7, context.subscription?.id)
        assertEquals("ACTIVE", context.subscription?.status)
        assertEquals("Luis Perez", context.clientName)
        assertTrue(context.identified)
        assertEquals(75.0, context.pendingDebt?.amount)
        assertEquals(2, context.pendingDebt?.invoiceCount)
        assertTrue(context.serviceWindowActive)
        assertEquals(1, context.recentLogs.size)
    }
}
