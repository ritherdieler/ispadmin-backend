package com.dscorp.wispadmin.wispadmin.service.whatsapp

import com.dscorp.wispadmin.wispadmin.config.WhatsAppProperties
import com.dscorp.wispadmin.wispadmin.data.model.EquipmentCondition
import com.dscorp.wispadmin.wispadmin.data.model.Payment
import com.dscorp.wispadmin.wispadmin.data.model.ServiceStatus
import com.dscorp.wispadmin.wispadmin.data.model.Subscription
import com.dscorp.wispadmin.wispadmin.data.model.WhatsAppInboundMessage
import com.dscorp.wispadmin.wispadmin.data.model.WhatsAppMessageLog
import com.dscorp.wispadmin.wispadmin.data.model.WhatsAppSyncedTemplate
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionRepository
import com.dscorp.wispadmin.wispadmin.repository.WhatsAppInboundMessageRepository
import com.dscorp.wispadmin.wispadmin.repository.WhatsAppMessageLogRepository
import com.dscorp.wispadmin.wispadmin.repository.WhatsAppSyncedTemplateRepository
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
    private val syncedTemplateRepository = mockk<WhatsAppSyncedTemplateRepository>()
    private val templateSyncService = mockk<WhatsAppTemplateSyncService>(relaxed = true)
    private val whatsAppProperties = WhatsAppProperties()
    private val templateDisplayService = WhatsAppTemplateDisplayService(
        syncedTemplateRepository = syncedTemplateRepository,
        templateSyncService = templateSyncService,
        whatsAppProperties = whatsAppProperties
    )

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
            crmTicketLinkService = crmTicketLinkService,
            templateDisplayService = templateDisplayService
        )
        every { crmTicketLinkService.listTicketsForPhone(any()) } returns emptyList()
        every { syncedTemplateRepository.findByName(any()) } returns null
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
        assertEquals(now.minusMinutes(5), result[0].lastInboundAt)
        assertEquals(0, result[0].unreadCount)
        assertFalse(result[0].identified)
        assertTrue(result[0].lastHasMedia)
        assertEquals(null, result[0].lastButtonReplyId)

        assertEquals("51911111111", result[1].phone)
        assertEquals("Auto reply", result[1].lastMessagePreview)
        assertEquals(now.minusMinutes(10), result[1].lastInboundAt)
        assertEquals(2, result[1].unreadCount)
        assertEquals("Ana Lopez", result[1].clientName)
        assertTrue(result[1].identified)
        assertTrue(result[1].serviceWindowActive)
        assertEquals("soporte", result[1].lastButtonReplyId)
        assertFalse(result[1].lastHasMedia)
    }

    @Test
    fun `listConversations ordena por lastInboundAt y no por outbound del bot`() {
        val now = LocalDateTime.of(2026, 8, 4, 15, 0)
        every { inboundMessageRepository.findTop500ByOrderByCreatedAtDesc() } returns listOf(
            WhatsAppInboundMessage(
                id = 1,
                metaMessageId = "in-old",
                phone = "51911111111",
                messageText = "Cliente viejo",
                createdAt = now.minusHours(2),
                readAt = null
            ),
            WhatsAppInboundMessage(
                id = 2,
                metaMessageId = "in-new",
                phone = "51922222222",
                messageText = "Cliente reciente",
                createdAt = now.minusMinutes(20),
                readAt = null
            )
        )
        every { messageLogRepository.findTop500ByOrderByCreatedAtDesc() } returns listOf(
            WhatsAppMessageLog(
                id = 100,
                phone = "51911111111",
                message = "[MAIN_MENU] Hola bot",
                messageType = "AUTO_REPLY",
                status = "SENT",
                createdAt = now.minusMinutes(1)
            )
        )
        every { subscriptionRepository.findAllById(emptyList()) } returns emptyList()
        every { serviceWindowService.getServiceWindows(any()) } returns emptyMap()

        val result = service.listConversations(WhatsAppConversationFilter(limit = 50))

        assertEquals(listOf("51922222222", "51911111111"), result.map { it.phone })
        assertEquals(now.minusMinutes(20), result[0].lastInboundAt)
        assertEquals(now.minusHours(2), result[1].lastInboundAt)
        assertEquals(now.minusMinutes(1), result[1].lastMessageAt)
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

        val page = service.getThread(phone)
        val thread = page.messages

        assertEquals(3, thread.size)
        assertFalse(page.hasMore)
        assertEquals(t1, page.nextBefore)
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

        val thread = service.getThread(phone).messages

        assertEquals(1, thread.size)
        assertEquals("PAYMENT_REMINDER", thread[0].templateCode)
        assertEquals("SENT", thread[0].deliveryStatus)
        assertEquals("Recordatorio", thread[0].body)
    }

    @Test
    fun `getThread renders legacy template log body from synced Meta text`() {
        val phone = "51902354183"
        every { syncedTemplateRepository.findByName("payment_reminder_gigaperu") } returns WhatsAppSyncedTemplate(
            metaTemplateId = "123",
            name = "payment_reminder_gigaperu",
            bodyText = "Hola {{customer_name}}, pague {{amount}} antes de {{billing_period}}."
        )
        every {
            inboundMessageRepository.findByPhoneOrderByCreatedAtDesc(phone, any<Pageable>())
        } returns emptyList()
        every {
            messageLogRepository.findByPhoneOrderByCreatedAtDesc(phone, any<Pageable>())
        } returns listOf(
            WhatsAppMessageLog(
                id = 9,
                phone = phone,
                message = "payment_reminder_gigaperu [customer_name=Ana Lopez, amount=50.0, billing_period=01/08/2026]",
                messageType = "PAYMENT_REMINDER",
                status = "SENT",
                createdAt = LocalDateTime.of(2026, 8, 3, 9, 0)
            )
        )

        val thread = service.getThread(phone).messages

        assertEquals(1, thread.size)
        assertEquals("Hola Ana Lopez, pague 50.0 antes de 01/08/2026.", thread[0].body)
    }

    @Test
    fun `getThread renders legacy template log with fallback when body sync missing`() {
        val phone = "51902354183"
        every { syncedTemplateRepository.findByName("payment_reminder_gigaperu") } returns null
        every {
            inboundMessageRepository.findByPhoneOrderByCreatedAtDesc(phone, any<Pageable>())
        } returns emptyList()
        every {
            messageLogRepository.findByPhoneOrderByCreatedAtDesc(phone, any<Pageable>())
        } returns listOf(
            WhatsAppMessageLog(
                id = 10,
                phone = phone,
                message = "payment_reminder_gigaperu [customer_name=Ana Lopez, amount=50.0, billing_period=01/08/2026]",
                messageType = "PAYMENT_REMINDER",
                status = "SENT",
                createdAt = LocalDateTime.of(2026, 8, 3, 9, 0)
            )
        )

        val thread = service.getThread(phone).messages

        assertEquals(
            "Estimado(a) Ana Lopez, le recordamos su pago pendiente de S/ 50.0 correspondiente al periodo 01/08/2026.",
            thread[0].body
        )
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

        assertEquals(26, pageableSlot.captured.pageSize)
        assertEquals(0, pageableSlot.captured.pageNumber)
    }

    @Test
    fun `getThread pagina con before y marca hasMore`() {
        val phone = "51902354183"
        val t1 = LocalDateTime.of(2026, 8, 4, 10, 0)
        val t2 = LocalDateTime.of(2026, 8, 4, 11, 0)
        val t3 = LocalDateTime.of(2026, 8, 4, 12, 0)
        every {
            inboundMessageRepository.findByPhoneOrderByCreatedAtDesc(phone, any<Pageable>())
        } returns listOf(
            WhatsAppInboundMessage(id = 3, metaMessageId = "in-3", phone = phone, messageText = "C", createdAt = t3),
            WhatsAppInboundMessage(id = 2, metaMessageId = "in-2", phone = phone, messageText = "B", createdAt = t2),
            WhatsAppInboundMessage(id = 1, metaMessageId = "in-1", phone = phone, messageText = "A", createdAt = t1),
        )
        every {
            messageLogRepository.findByPhoneOrderByCreatedAtDesc(phone, any<Pageable>())
        } returns emptyList()

        val first = service.getThread(phone, limit = 2)
        assertEquals(listOf("inbound:2", "inbound:3"), first.messages.map { it.id })
        assertTrue(first.hasMore)
        assertEquals(t2, first.nextBefore)

        every {
            inboundMessageRepository.findByPhoneAndCreatedAtLessThanOrderByCreatedAtDesc(phone, t2, any<Pageable>())
        } returns listOf(
            WhatsAppInboundMessage(id = 1, metaMessageId = "in-1", phone = phone, messageText = "A", createdAt = t1),
        )
        every {
            messageLogRepository.findByPhoneAndCreatedAtLessThanOrderByCreatedAtDesc(phone, t2, any<Pageable>())
        } returns emptyList()

        val older = service.getThread(phone, limit = 2, before = t2)
        assertEquals(listOf("inbound:1"), older.messages.map { it.id })
        assertFalse(older.hasMore)
        assertEquals(t1, older.nextBefore)
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
