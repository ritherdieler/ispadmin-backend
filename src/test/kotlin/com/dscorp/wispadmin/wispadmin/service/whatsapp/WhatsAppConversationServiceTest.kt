package com.dscorp.wispadmin.wispadmin.service.whatsapp

import com.dscorp.wispadmin.wispadmin.config.WhatsAppAutoReplyProperties
import com.dscorp.wispadmin.wispadmin.config.WhatsAppProperties
import com.dscorp.wispadmin.wispadmin.data.model.EquipmentCondition
import com.dscorp.wispadmin.wispadmin.data.model.InstallationType
import com.dscorp.wispadmin.wispadmin.data.model.Payment
import com.dscorp.wispadmin.wispadmin.data.model.ServiceStatus
import com.dscorp.wispadmin.wispadmin.data.model.Subscription
import com.dscorp.wispadmin.wispadmin.data.model.WhatsAppChatState
import com.dscorp.wispadmin.wispadmin.data.model.WhatsAppConversationStep
import com.dscorp.wispadmin.wispadmin.data.model.WhatsAppInboundMessage
import com.dscorp.wispadmin.wispadmin.data.model.WhatsAppMessageLog
import com.dscorp.wispadmin.wispadmin.repository.PaymentRepository
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionRepository
import com.dscorp.wispadmin.wispadmin.repository.WhatsAppInboundMessageRepository
import com.dscorp.wispadmin.wispadmin.repository.WhatsAppMessageLogRepository
import com.dscorp.wispadmin.wispadmin.service.WhatsAppSendResult
import com.dscorp.wispadmin.wispadmin.service.WhatsAppService
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class WhatsAppConversationServiceTest {

    private val whatsAppService = mockk<WhatsAppService>()
    private val subscriptionRepository = mockk<SubscriptionRepository>()
    private val paymentRepository = mockk<PaymentRepository>()
    private val messageLogRepository = mockk<WhatsAppMessageLogRepository>()
    private val inboundMessageRepository = mockk<WhatsAppInboundMessageRepository>()
    private val serviceWindowService = mockk<WhatsAppServiceWindowService>()
    private val intentRouter = WhatsAppInboundIntentRouter()
    private val chatStateService = mockk<WhatsAppChatStateService>()
    private val whatsAppProperties = WhatsAppProperties().apply {
        autoReply = WhatsAppAutoReplyProperties()
    }

    private lateinit var service: WhatsAppConversationService

    @BeforeEach
    fun setUp() {
        service = WhatsAppConversationService(
            whatsAppService = whatsAppService,
            subscriptionRepository = subscriptionRepository,
            paymentRepository = paymentRepository,
            messageLogRepository = messageLogRepository,
            inboundMessageRepository = inboundMessageRepository,
            serviceWindowService = serviceWindowService,
            whatsAppProperties = whatsAppProperties,
            intentRouter = intentRouter,
            chatStateService = chatStateService
        )
        every { chatStateService.currentStep(any()) } returns null
        every { chatStateService.hasPendingSupportDiagnostic(any()) } returns false
        every { chatStateService.hasPendingInteractiveMenu(any()) } returns false
        every { chatStateService.setCurrentStep(any(), any()) } answers {
            WhatsAppChatState(
                phone = firstArg(),
                currentStep = secondArg()
            )
        }
    }

    @Test
    fun `handleButtonReply ver_deuda mentions pending amount and payment info`() {
        val subscription = Subscription(
            firstName = "Ana",
            lastName = "Lopez",
            phone = "902354183",
            serviceStatus = ServiceStatus.ACTIVE,
            equipmentCondition = EquipmentCondition.LOAN
        ).apply { id = 10 }

        every {
            paymentRepository.findUnpaidBySubscriptionIdOrderByBillingDateDatetimeAsc(10)
        } returns listOf(
            Payment(
                discountAmount = 0.0,
                paid = false,
                amountToPay = 50.0,
                billingDateDatetime = LocalDateTime.of(2026, 5, 31, 0, 0)
            )
        )

        val response = service.handleButtonReply(
            "51902354183",
            WhatsAppConversationService.BUTTON_DEBT,
            subscription
        )
        assertTrue(response.contains("50.00"))
        assertTrue(response.contains("Ana Lopez"))
        assertTrue(response.contains("BCP"))
        assertTrue(response.contains("Yape"))
    }

    @Test
    fun `buildPaidResponse confirms when no unpaid invoices`() {
        val subscription = Subscription(
            firstName = "Julio",
            lastName = "Paisic",
            phone = "902354183",
            serviceStatus = ServiceStatus.ACTIVE,
            equipmentCondition = EquipmentCondition.LOAN
        ).apply { id = 11 }

        every {
            paymentRepository.findUnpaidBySubscriptionIdOrderByBillingDateDatetimeAsc(11)
        } returns emptyList()

        val response = service.buildPaidResponse(subscription)
        assertTrue(response.contains("ya fue registrado"))
        assertTrue(response.contains("al dia"))
    }

    @Test
    fun `buildPaidResponse asks voucher when unpaid remain`() {
        val subscription = Subscription(
            firstName = "Julio",
            lastName = "Paisic",
            phone = "902354183",
            serviceStatus = ServiceStatus.CUT_OFF,
            equipmentCondition = EquipmentCondition.LOAN
        ).apply { id = 12 }

        every {
            paymentRepository.findUnpaidBySubscriptionIdOrderByBillingDateDatetimeAsc(12)
        } returns listOf(
            Payment(
                discountAmount = 0.0,
                paid = false,
                amountToPay = 50.0,
                billingDateDatetime = LocalDateTime.of(2026, 5, 31, 0, 0)
            )
        )

        val response = service.buildPaidResponse(subscription)
        assertTrue(response.contains("voucher"))
        assertTrue(response.contains("cortado"))
    }

    @Test
    fun `handleSupport returns segmented internet menu without creating ticket`() {
        val subscription = Subscription(
            firstName = "Ana",
            lastName = "Lopez",
            phone = "902354183",
            serviceStatus = ServiceStatus.ACTIVE,
            installationType = InstallationType.FIBER,
            equipmentCondition = EquipmentCondition.LOAN
        ).apply { id = 20 }

        val response = service.handleSupportOrTechnicalIssue(
            subscription = subscription,
            phone = "51902354183",
            messageText = "tengo el internet lento",
            fromButton = false
        )

        assertTrue(response.contains("[SUPPORT_MENU:SOLO_INTERNET]"))
        assertTrue(response.contains("Sin Internet"))
        assertTrue(response.contains("Internet lento"))
    }

    @Test
    fun `handleSupport returns cable menu for only tv fiber`() {
        val subscription = Subscription(
            firstName = "Ana",
            lastName = "Lopez",
            phone = "902354183",
            serviceStatus = ServiceStatus.ACTIVE,
            installationType = InstallationType.ONLY_TV_FIBER,
            equipmentCondition = EquipmentCondition.LOAN
        ).apply { id = 21 }

        val response = service.handleSupportOrTechnicalIssue(
            subscription = subscription,
            phone = "51902354183",
            messageText = "soporte",
            fromButton = true
        )

        assertTrue(response.contains("[SUPPORT_MENU:SOLO_CABLE]"))
        assertTrue(response.contains("Sin señal"))
        assertTrue(response.contains("Imagen congelada"))
    }

    @Test
    fun `support menu selection asks fiber diagnostic question`() {
        val phone = "51902354183"
        val subscription = Subscription(
            firstName = "Ana",
            lastName = "Lopez",
            phone = "902354183",
            serviceStatus = ServiceStatus.ACTIVE,
            installationType = InstallationType.FIBER,
            equipmentCondition = EquipmentCondition.LOAN
        ).apply { id = 22 }

        every { chatStateService.currentStep(phone) } returns WhatsAppConversationStep.SUPPORT_MENU

        val response = service.handleSupportDiagnosticReply(subscription, phone, "1")

        assertTrue(response.contains("[SUPPORT_DIAG:NO_INTERNET]"))
        assertTrue(response.contains("LOS o PON"))
        assertTrue(response.contains("Luz roja"))
    }

    @Test
    fun `support diagnostic answer closes flow without creating ticket`() {
        val phone = "51902354183"
        val subscription = Subscription(
            firstName = "Ana",
            lastName = "Lopez",
            phone = "902354183",
            serviceStatus = ServiceStatus.ACTIVE,
            installationType = InstallationType.FIBER,
            equipmentCondition = EquipmentCondition.LOAN
        ).apply { id = 23 }

        every { chatStateService.currentStep(phone) } returns WhatsAppConversationStep.SUPPORT_DIAG
        val response = service.handleSupportDiagnosticReply(subscription, phone, "A")

        assertTrue(response.contains("[SUPPORT_CLOSED:ESPERANDO_ASESOR]"))
        assertTrue(response.contains("Tu caso ha sido registrado"))
    }

    @Test
    fun `hasPendingSupportDiagnostic ignores expired support state`() {
        val phone = "51902354183"
        every { chatStateService.hasPendingSupportDiagnostic(phone) } returns false

        assertFalse(service.hasPendingSupportDiagnostic(phone))
    }

    @Test
    fun `sendOperatorReply persists OPERATOR_REPLY when service window is open`() {
        val phone = "51902354183"
        every { serviceWindowService.getServiceWindow(phone) } returns
            WhatsAppServiceWindowService.WhatsAppServiceWindowStatus(
                phone = phone,
                open = true,
                expiresAt = LocalDateTime.now().plusHours(2)
            )
        every { subscriptionRepository.findByNormalizedPhone("902354183") } returns emptyList()
        every {
            whatsAppService.sendTextMessage(phone, "Hola cliente")
        } returns WhatsAppSendResult(
            success = true,
            metaResponse = "{}",
            metaMessageId = "wamid.op-1",
            recipient = phone,
            senderPhoneNumberId = "123"
        )
        val savedSlot = slot<WhatsAppMessageLog>()
        every { messageLogRepository.save(capture(savedSlot)) } answers {
            firstArg<WhatsAppMessageLog>().copy(id = 55)
        }

        val result = service.sendOperatorReply(phone, "Hola cliente", "operador1")

        assertEquals("outbound:55", result.id)
        assertEquals("OUTBOUND", result.direction)
        assertEquals("OPERATOR_REPLY", result.messageType)
        assertEquals("operador1", result.operatorUsername)
        assertEquals("Hola cliente", result.body)
        assertEquals("OPERATOR_REPLY", savedSlot.captured.messageType)
        assertEquals("SENT", savedSlot.captured.status)
        assertEquals("wamid.op-1", savedSlot.captured.metaMessageId)
    }

    @Test
    fun `sendOperatorReply rejects when service window is closed`() {
        val phone = "51902354183"
        every { serviceWindowService.getServiceWindow(phone) } returns
            WhatsAppServiceWindowService.WhatsAppServiceWindowStatus(
                phone = phone,
                open = false,
                expiresAt = LocalDateTime.now().minusMinutes(1)
            )

        assertThrows(IllegalArgumentException::class.java) {
            service.sendOperatorReply(phone, "Hola", "operador1")
        }
        verify(exactly = 0) { whatsAppService.sendTextMessage(any(), any()) }
        verify(exactly = 0) { messageLogRepository.save(any()) }
    }

    @Test
    fun `markInboundAsRead persists readAt locally`() {
        val inbound = WhatsAppInboundMessage(
            id = 8,
            metaMessageId = "wamid.in-8",
            phone = "51902354183",
            messageText = "Hola",
            readAt = null
        )
        every { whatsAppService.markMessageAsRead("wamid.in-8") } returns WhatsAppSendResult(
            success = true,
            metaResponse = "{}",
            metaMessageId = null,
            recipient = null,
            senderPhoneNumberId = "123"
        )
        val savedSlot = slot<WhatsAppInboundMessage>()
        every { inboundMessageRepository.save(capture(savedSlot)) } answers { firstArg() }

        val success = service.markInboundAsRead(inbound)

        assertTrue(success)
        assertNotNull(savedSlot.captured.readAt)
    }

    @Test
    fun `markAllRead persists readAt for all unread inbound of phone`() {
        val phone = "51902354183"
        val unread = listOf(
            WhatsAppInboundMessage(id = 1, metaMessageId = "m1", phone = phone, readAt = null),
            WhatsAppInboundMessage(id = 2, metaMessageId = "m2", phone = phone, readAt = null)
        )
        every { inboundMessageRepository.findByPhoneAndReadAtIsNull(phone) } returns unread
        every { whatsAppService.markMessageAsRead(any()) } returns WhatsAppSendResult(
            success = true,
            metaResponse = "{}",
            metaMessageId = null,
            recipient = null,
            senderPhoneNumberId = "123"
        )
        every { inboundMessageRepository.save(any()) } answers { firstArg() }

        val result = service.markAllRead(phone)

        assertEquals(2, result.markedCount)
        assertTrue(result.success)
        verify(exactly = 2) { inboundMessageRepository.save(match { it.readAt != null }) }
    }

    @Test
    fun `hasRecentOperatorReply uses repository lookup`() {
        every {
            messageLogRepository.existsByPhoneAndMessageTypeAndCreatedAtAfter(
                "51902354183",
                WhatsAppConversationService.MESSAGE_TYPE_OPERATOR_REPLY,
                any()
            )
        } returns true

        assertTrue(service.hasRecentOperatorReply("51902354183"))
    }

    @Test
    fun `isInboundBurst when count is at least two`() {
        every {
            inboundMessageRepository.countByPhoneAndCreatedAtAfter("51902354183", any())
        } returns 2

        assertTrue(service.isInboundBurst("51902354183"))
        every {
            inboundMessageRepository.countByPhoneAndCreatedAtAfter("51902354183", any())
        } returns 1
        assertFalse(service.isInboundBurst("51902354183"))
    }
}
