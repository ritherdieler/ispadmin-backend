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
import com.dscorp.wispadmin.wispadmin.service.WhatsAppSendResult
import com.dscorp.wispadmin.wispadmin.service.WhatsAppService
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class WhatsAppConversationServiceTest {

    private val whatsAppService = mockk<WhatsAppService>()
    private val subscriptionRepository = mockk<SubscriptionRepository>()
    private val messageLogRepository = mockk<WhatsAppMessageLogRepository>()
    private val inboundMessageRepository = mockk<WhatsAppInboundMessageRepository>()
    private val serviceWindowService = mockk<WhatsAppServiceWindowService>()

    private lateinit var service: WhatsAppConversationService

    @BeforeEach
    fun setUp() {
        service = WhatsAppConversationService(
            whatsAppService = whatsAppService,
            subscriptionRepository = subscriptionRepository,
            messageLogRepository = messageLogRepository,
            inboundMessageRepository = inboundMessageRepository,
            serviceWindowService = serviceWindowService
        )
    }

    @Test
    fun `handleButtonReply ver_deuda mentions pending amount`() {
        val subscription = Subscription(
            firstName = "Ana",
            lastName = "Lopez",
            phone = "902354183",
            serviceStatus = ServiceStatus.ACTIVE,
            equipmentCondition = EquipmentCondition.LOAN
        )
        subscription.payments = mutableSetOf(
            Payment(
                discountAmount = 0.0,
                paid = false,
                amountToPay = 50.0,
                billingDateDatetime = LocalDateTime.now()
            )
        )

        val response = service.handleButtonReply(
            "51902354183",
            WhatsAppConversationService.BUTTON_DEBT,
            subscription
        )
        assertTrue(response.contains("50.00"))
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
}
