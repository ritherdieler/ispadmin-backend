package com.dscorp.wispadmin.wispadmin.service

import com.dscorp.wispadmin.wispadmin.config.WhatsAppAutoReplyProperties
import com.dscorp.wispadmin.wispadmin.config.WhatsAppInboundAlertProperties
import com.dscorp.wispadmin.wispadmin.config.WhatsAppProperties
import com.dscorp.wispadmin.wispadmin.data.model.EquipmentCondition
import com.dscorp.wispadmin.wispadmin.data.model.ServiceStatus
import com.dscorp.wispadmin.wispadmin.data.model.Subscription
import com.dscorp.wispadmin.wispadmin.data.model.WhatsAppInboundMessage
import com.dscorp.wispadmin.wispadmin.data.model.WhatsAppMessageLog
import com.dscorp.wispadmin.wispadmin.data.model.WhatsAppConversationStep
import com.dscorp.wispadmin.wispadmin.repository.WhatsAppInboundMessageRepository
import com.dscorp.wispadmin.wispadmin.repository.WhatsAppMessageLogRepository
import com.dscorp.wispadmin.wispadmin.data.model.CrmChannel
import com.dscorp.wispadmin.wispadmin.data.model.CrmConversation
import com.dscorp.wispadmin.wispadmin.data.model.CrmConversationStatus
import com.dscorp.wispadmin.wispadmin.service.whatsapp.CrmConversationService
import com.dscorp.wispadmin.wispadmin.service.whatsapp.CrmEventPublisher
import com.dscorp.wispadmin.wispadmin.service.whatsapp.CrmTicketLinkService
import com.dscorp.wispadmin.wispadmin.service.whatsapp.CsatSurveyService
import com.dscorp.wispadmin.wispadmin.service.whatsapp.WhatsAppChatStateService
import com.dscorp.wispadmin.wispadmin.service.whatsapp.WhatsAppConversationService
import com.dscorp.wispadmin.wispadmin.service.whatsapp.WhatsAppHandoffResult
import com.dscorp.wispadmin.wispadmin.service.whatsapp.WhatsAppHandoffService
import com.dscorp.wispadmin.wispadmin.service.whatsapp.WhatsAppInboundSession
import com.dscorp.wispadmin.wispadmin.config.CrmLlmProperties
import com.dscorp.wispadmin.wispadmin.service.whatsapp.LlmClient
import com.dscorp.wispadmin.wispadmin.service.whatsapp.WhatsAppAuditService
import com.dscorp.wispadmin.wispadmin.service.whatsapp.WhatsAppInboundIntentRouter
import com.dscorp.wispadmin.wispadmin.service.whatsapp.WhatsAppIntentClassifier
import com.dscorp.wispadmin.wispadmin.service.whatsapp.WhatsAppInboundPayload
import com.dscorp.wispadmin.wispadmin.service.whatsapp.WhatsAppMediaDownloadService
import com.google.firebase.messaging.FirebaseMessaging
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class WhatsAppInboundMessageServiceTest {

    private val inboundMessageRepository = mockk<WhatsAppInboundMessageRepository>()
    private val conversationService = mockk<WhatsAppConversationService>()
    private val mediaDownloadService = mockk<WhatsAppMediaDownloadService>()
    private val whatsAppService = mockk<WhatsAppService>()
    private val messageLogRepository = mockk<WhatsAppMessageLogRepository>()
    private val fcm = mockk<FirebaseMessaging>(relaxed = true)
    private val chatStateService = mockk<WhatsAppChatStateService>()
    private val handoffService = mockk<WhatsAppHandoffService>()
    private val crmEventPublisher = mockk<CrmEventPublisher>(relaxed = true)
    private val crmConversationService = mockk<CrmConversationService>(relaxed = true)
    private val crmTicketLinkService = mockk<CrmTicketLinkService>(relaxed = true)
    private val csatSurveyService = mockk<CsatSurveyService>(relaxed = true)
    private val intentRouter = WhatsAppInboundIntentRouter()
    private val llmClient = mockk<LlmClient>(relaxed = true)
    private val auditService = mockk<WhatsAppAuditService>(relaxed = true)
    private lateinit var intentClassifier: WhatsAppIntentClassifier
    private val whatsAppProperties = WhatsAppProperties().apply {
        autoReply = WhatsAppAutoReplyProperties().apply {
            businessHours = "MON-SUN|00:00-23:59"
        }
        inboundAlert = WhatsAppInboundAlertProperties().apply { enabled = false }
    }

    private lateinit var service: WhatsAppInboundMessageService

    @BeforeEach
    fun setUp() {
        intentClassifier = WhatsAppIntentClassifier(
            rulesRouter = intentRouter,
            llmClient = llmClient,
            chatStateService = chatStateService,
            auditService = auditService,
            properties = CrmLlmProperties()
        )
        service = WhatsAppInboundMessageService(
            inboundMessageRepository = inboundMessageRepository,
            conversationService = conversationService,
            mediaDownloadService = mediaDownloadService,
            whatsAppService = whatsAppService,
            messageLogRepository = messageLogRepository,
            intentRouter = intentRouter,
            intentClassifier = intentClassifier,
            whatsAppProperties = whatsAppProperties,
            fcm = fcm,
            chatStateService = chatStateService,
            handoffService = handoffService,
            crmEventPublisher = crmEventPublisher,
            crmConversationService = crmConversationService,
            crmTicketLinkService = crmTicketLinkService,
            csatSurveyService = csatSurveyService
        )
        every { csatSurveyService.tryHandleInbound(any(), any(), any(), any(), any()) } returns false
        every { chatStateService.isBotPaused(any()) } returns false
        every { chatStateService.beginInboundInteraction(any()) } returns WhatsAppInboundSession(
            botPaused = false,
            isNewOrExpired = false,
            lastInteractionAt = LocalDateTime.now(),
            currentStep = WhatsAppConversationStep.MAIN_MENU
        )
        every { chatStateService.hasPendingInteractiveMenu(any()) } returns false
        every { chatStateService.getUnknownRetryCount(any()) } returns 0
        every { chatStateService.incrementUnknownRetryCount(any()) } returns 1
        every { chatStateService.resetUnknownRetryCount(any()) } returns Unit
        every { llmClient.classifyIntent(any()) } returns null
        every { handoffService.pauseBotAndPassToAdvisor(any(), any()) } returns
            WhatsAppHandoffResult(botPaused = true, metaTransferred = false)
        every { inboundMessageRepository.findByPhoneAndReadAtIsNull(any()) } returns emptyList()
        every { crmConversationService.touchInbound(any(), any()) } answers {
            CrmConversation(
                id = 99L,
                channel = CrmChannel.WHATSAPP,
                phone = firstArg(),
                subscriptionId = secondArg(),
                status = CrmConversationStatus.NEW,
                lastInboundAt = LocalDateTime.now()
            )
        }
    }

    @Test
    fun `processInboundMessage persists AUTO_REPLY log after successful button reply`() {
        val payload = WhatsAppInboundPayload(
            metaMessageId = "wamid.in-1",
            phone = "51902354183",
            messageText = null,
            messageType = "button_reply",
            buttonReplyId = "soporte",
            buttonReplyTitle = "Soporte",
            mediaId = null,
            mediaMimeType = null,
            contextMessageId = null
        )
        every { conversationService.resolveReplyToLogId(null) } returns null
        every { inboundMessageRepository.save(any()) } answers {
            val msg = firstArg<WhatsAppInboundMessage>()
            if (msg.id == null) msg.copy(id = 1) else msg
        }
        every { conversationService.findSubscriptionByPhone(payload.phone) } returns null
        every { conversationService.hasRecentOperatorReply(payload.phone) } returns false
        every { conversationService.isSupportEntryButton("soporte") } returns false
        every { conversationService.isSupportDiagnosticButton("soporte") } returns false
        every {
            conversationService.sendSupportEntryMenu(payload.phone, null)
        } returns WhatsAppConversationService.AutoReplyResult(
            success = true,
            messageText = "[SUPPORT_MENU:SOLO_INTERNET] Selecciona el problema",
            metaMessageId = "wamid.auto-9",
        )
        val logSlot = slot<WhatsAppMessageLog>()
        every { messageLogRepository.save(capture(logSlot)) } answers { firstArg() }

        service.processInboundMessage(payload)

        assertEquals("AUTO_REPLY", logSlot.captured.messageType)
        assertEquals("SENT", logSlot.captured.status)
        assertTrue(logSlot.captured.message!!.contains("[SUPPORT_MENU:SOLO_INTERNET]"))
        assertEquals("wamid.auto-9", logSlot.captured.metaMessageId)
        assertEquals(payload.phone, logSlot.captured.phone)
    }

    @Test
    fun `ok text does not send interactive menu`() {
        val payload = WhatsAppInboundPayload(
            metaMessageId = "wamid.in-2",
            phone = "51902354183",
            messageText = "ok",
            messageType = "text",
            buttonReplyId = null,
            buttonReplyTitle = null,
            mediaId = null,
            mediaMimeType = null,
            contextMessageId = null
        )
        every { conversationService.resolveReplyToLogId(null) } returns null
        every { inboundMessageRepository.save(any()) } answers {
            val msg = firstArg<WhatsAppInboundMessage>()
            if (msg.id == null) msg.copy(id = 2) else msg
        }
        every { conversationService.findSubscriptionByPhone(payload.phone) } returns null
        every { conversationService.hasRecentOperatorReply(payload.phone) } returns false
        every { conversationService.isInboundBurst(payload.phone) } returns false
        every { conversationService.hasPendingInteractiveMenu(payload.phone) } returns false
        every {
            conversationService.buildAckResponse(null)
        } returns "Para continuar, por favor selecciona una de las opciones del menú en pantalla 👇"
        every {
            whatsAppService.sendTextMessage(payload.phone, any())
        } returns WhatsAppSendResult(
            success = true,
            metaResponse = "{}",
            metaMessageId = "wamid.ack",
            recipient = payload.phone,
            senderPhoneNumberId = "123"
        )
        every { messageLogRepository.save(any()) } answers { firstArg() }

        service.processInboundMessage(payload)

        verify(exactly = 0) { conversationService.sendAutoReplyWithButtons(any(), any()) }
        verify(exactly = 1) { conversationService.buildAckResponse(null) }
    }

    @Test
    fun `greeting starts main menu`() {
        val payload = WhatsAppInboundPayload(
            metaMessageId = "wamid.in-3",
            phone = "51902354183",
            messageText = "Hola",
            messageType = "text",
            buttonReplyId = null,
            buttonReplyTitle = null,
            mediaId = null,
            mediaMimeType = null,
            contextMessageId = null
        )
        every { conversationService.resolveReplyToLogId(null) } returns null
        every { inboundMessageRepository.save(any()) } answers {
            val msg = firstArg<WhatsAppInboundMessage>()
            if (msg.id == null) msg.copy(id = 3) else msg
        }
        every { conversationService.findSubscriptionByPhone(payload.phone) } returns null
        every { conversationService.hasRecentOperatorReply(payload.phone) } returns false
        every { conversationService.isInboundBurst(payload.phone) } returns false
        every { conversationService.hasPendingInteractiveMenu(payload.phone) } returns false
        every { chatStateService.beginInboundInteraction(payload.phone) } returns WhatsAppInboundSession(
            botPaused = false,
            isNewOrExpired = true,
            lastInteractionAt = null,
            currentStep = WhatsAppConversationStep.MAIN_MENU
        )
        every {
            conversationService.sendMainMenu(payload.phone, null, true)
        } returns WhatsAppConversationService.AutoReplyResult(
            success = true,
            messageText = "[MAIN_MENU] Bienvenida",
            metaMessageId = "wamid.main-menu"
        )
        val logSlot = slot<WhatsAppMessageLog>()
        every { messageLogRepository.save(capture(logSlot)) } answers { firstArg() }

        service.processInboundMessage(payload)

        verify(exactly = 1) { conversationService.sendMainMenu(payload.phone, null, true) }
        assertEquals("AUTO_REPLY", logSlot.captured.messageType)
        assertEquals("wamid.main-menu", logSlot.captured.metaMessageId)
    }

    @Test
    fun `active session greeting opens main menu without initial greeting`() {
        val payload = WhatsAppInboundPayload(
            metaMessageId = "wamid.in-3b",
            phone = "51902354183",
            messageText = "Hola",
            messageType = "text",
            buttonReplyId = null,
            buttonReplyTitle = null,
            mediaId = null,
            mediaMimeType = null,
            contextMessageId = null
        )
        every { conversationService.resolveReplyToLogId(null) } returns null
        every { inboundMessageRepository.save(any()) } answers {
            val msg = firstArg<WhatsAppInboundMessage>()
            if (msg.id == null) msg.copy(id = 33) else msg
        }
        every { conversationService.findSubscriptionByPhone(payload.phone) } returns null
        every { conversationService.hasRecentOperatorReply(payload.phone) } returns false
        every { conversationService.isInboundBurst(payload.phone) } returns false
        every {
            conversationService.sendMainMenu(payload.phone, null, false)
        } returns WhatsAppConversationService.AutoReplyResult(
            success = true,
            messageText = "[MAIN_MENU] Selecciona una opcion para continuar",
            metaMessageId = "wamid.main-menu-active"
        )
        val logSlot = slot<WhatsAppMessageLog>()
        every { messageLogRepository.save(capture(logSlot)) } answers { firstArg() }

        service.processInboundMessage(payload)

        verify(exactly = 1) { conversationService.sendMainMenu(payload.phone, null, false) }
        assertEquals("wamid.main-menu-active", logSlot.captured.metaMessageId)
    }

    @Test
    fun `debt flow persists rendered message with client full name`() {
        val payload = WhatsAppInboundPayload(
            metaMessageId = "wamid.in-debt",
            phone = "51902354183",
            messageText = null,
            messageType = "button_reply",
            buttonReplyId = WhatsAppConversationService.BUTTON_DEBT,
            buttonReplyTitle = "Deuda",
            mediaId = null,
            mediaMimeType = null,
            contextMessageId = null
        )
        val subscription = Subscription(
            firstName = "Ana",
            lastName = "Lopez",
            phone = "902354183",
            serviceStatus = ServiceStatus.ACTIVE,
            equipmentCondition = EquipmentCondition.LOAN
        ).apply { id = 88 }
        every { conversationService.resolveReplyToLogId(null) } returns null
        every { inboundMessageRepository.save(any()) } answers {
            val msg = firstArg<WhatsAppInboundMessage>()
            if (msg.id == null) msg.copy(id = 88) else msg
        }
        every { conversationService.findSubscriptionByPhone(payload.phone) } returns subscription
        every { conversationService.hasRecentOperatorReply(payload.phone) } returns false
        every { conversationService.isSupportEntryButton(WhatsAppConversationService.BUTTON_DEBT) } returns false
        every { conversationService.isSupportDiagnosticButton(WhatsAppConversationService.BUTTON_DEBT) } returns false
        every {
            conversationService.sendDebtResponseMenu(payload.phone, subscription)
        } returns WhatsAppConversationService.AutoReplyResult(
            success = true,
            messageText = "[DEUDA] Estimado(a) Ana Lopez, tu saldo pendiente al dia de hoy es S/ 50.00.",
            metaMessageId = "wamid.debt"
        )
        val logSlot = slot<WhatsAppMessageLog>()
        every { messageLogRepository.save(capture(logSlot)) } answers { firstArg() }

        service.processInboundMessage(payload)

        assertTrue(logSlot.captured.message!!.contains("Ana Lopez"))
        assertEquals("wamid.debt", logSlot.captured.metaMessageId)
    }

    @Test
    fun `text manual returns guardrail when interactive menu is pending`() {
        val payload = WhatsAppInboundPayload(
            metaMessageId = "wamid.in-4",
            phone = "51902354183",
            messageText = "Buenas tardes tengo internet lento",
            messageType = "text",
            buttonReplyId = null,
            buttonReplyTitle = null,
            mediaId = null,
            mediaMimeType = null,
            contextMessageId = null
        )
        every { conversationService.resolveReplyToLogId(null) } returns null
        every { inboundMessageRepository.save(any()) } answers {
            val msg = firstArg<WhatsAppInboundMessage>()
            if (msg.id == null) msg.copy(id = 4) else msg
        }
        every { conversationService.findSubscriptionByPhone(payload.phone) } returns null
        every { conversationService.hasRecentOperatorReply(payload.phone) } returns false
        every { conversationService.isInboundBurst(payload.phone) } returns false
        every { chatStateService.hasPendingInteractiveMenu(payload.phone) } returns true
        every { conversationService.invalidInteractiveSelectionText() } returns
            "Para continuar, por favor selecciona una de las opciones del menú en pantalla 👇"
        every {
            whatsAppService.sendTextMessage(payload.phone, any())
        } returns WhatsAppSendResult(
            success = true,
            metaResponse = "{}",
            metaMessageId = "wamid.guardrail",
            recipient = payload.phone,
            senderPhoneNumberId = "123"
        )
        val logSlot = slot<WhatsAppMessageLog>()
        every { messageLogRepository.save(capture(logSlot)) } answers { firstArg() }

        service.processInboundMessage(payload)

        verify(exactly = 0) { conversationService.sendSupportEntryMenu(any(), any()) }
        verify(exactly = 1) { conversationService.invalidInteractiveSelectionText() }
        assertTrue(logSlot.captured.message!!.contains("[MENU_INVALID]"))
        assertEquals("wamid.guardrail", logSlot.captured.metaMessageId)
    }

    @Test
    fun `technical issue text bypasses inbound burst protection`() {
        val payload = WhatsAppInboundPayload(
            metaMessageId = "wamid.in-6",
            phone = "51965754000",
            messageText = "Tengo internet lento",
            messageType = "text",
            buttonReplyId = null,
            buttonReplyTitle = null,
            mediaId = null,
            mediaMimeType = null,
            contextMessageId = null
        )
        every { conversationService.resolveReplyToLogId(null) } returns null
        every { inboundMessageRepository.save(any()) } answers {
            val msg = firstArg<WhatsAppInboundMessage>()
            if (msg.id == null) msg.copy(id = 6) else msg
        }
        every { conversationService.findSubscriptionByPhone(payload.phone) } returns null
        every { conversationService.hasRecentOperatorReply(payload.phone) } returns false
        every { conversationService.isInboundBurst(payload.phone) } returns true
        every { conversationService.hasPendingInteractiveMenu(payload.phone) } returns false
        every {
            conversationService.sendSupportEntryMenu(payload.phone, null)
        } returns WhatsAppConversationService.AutoReplyResult(
            success = true,
            messageText = "[SUPPORT_MENU:SOLO_INTERNET] Selecciona el problema",
            metaMessageId = "wamid.burst-support"
        )
        val logSlot = slot<WhatsAppMessageLog>()
        every { messageLogRepository.save(capture(logSlot)) } answers { firstArg() }

        service.processInboundMessage(payload)

        verify(exactly = 1) { conversationService.sendSupportEntryMenu(payload.phone, null) }
        assertEquals("wamid.burst-support", logSlot.captured.metaMessageId)
    }

    @Test
    fun `operator silence skips auto reply`() {
        val payload = WhatsAppInboundPayload(
            metaMessageId = "wamid.in-5",
            phone = "51902354183",
            messageText = "hola",
            messageType = "text",
            buttonReplyId = null,
            buttonReplyTitle = null,
            mediaId = null,
            mediaMimeType = null,
            contextMessageId = null
        )
        every { conversationService.resolveReplyToLogId(null) } returns null
        every { inboundMessageRepository.save(any()) } answers {
            val msg = firstArg<WhatsAppInboundMessage>()
            if (msg.id == null) msg.copy(id = 5) else msg
        }
        every { conversationService.findSubscriptionByPhone(payload.phone) } returns null
        every { conversationService.hasRecentOperatorReply(payload.phone) } returns true

        service.processInboundMessage(payload)

        verify(exactly = 0) { whatsAppService.sendTextMessage(any(), any()) }
        verify(exactly = 0) { conversationService.sendAutoReplyWithButtons(any(), any()) }
    }

    @Test
    fun `processInboundMessage publishes MESSAGE_RECEIVED and CONVERSATION_UPDATED`() {
        val payload = WhatsAppInboundPayload(
            metaMessageId = "wamid.in-rt-1",
            phone = "51902354183",
            messageText = "necesito ayuda",
            messageType = "text",
            buttonReplyId = null,
            buttonReplyTitle = null,
            mediaId = null,
            mediaMimeType = null,
            contextMessageId = null
        )
        every { conversationService.resolveReplyToLogId(null) } returns null
        every { inboundMessageRepository.save(any()) } answers {
            val msg = firstArg<WhatsAppInboundMessage>()
            if (msg.id == null) msg.copy(id = 99) else msg
        }
        every { conversationService.findSubscriptionByPhone(payload.phone) } returns null
        every { conversationService.hasRecentOperatorReply(payload.phone) } returns true
        every { inboundMessageRepository.findByPhoneAndReadAtIsNull(payload.phone) } returns listOf(
            WhatsAppInboundMessage(id = 99, phone = payload.phone, metaMessageId = payload.metaMessageId)
        )

        service.processInboundMessage(payload)

        verify {
            crmEventPublisher.publish(
                CrmEventPublisher.MESSAGE_RECEIVED,
                match { it["phone"] == payload.phone && it["inboundMessageId"] == 99 }
            )
        }
        verify {
            crmEventPublisher.publish(
                CrmEventPublisher.CONVERSATION_UPDATED,
                match { it["phone"] == payload.phone && it["unreadCount"] == 1 }
            )
        }
    }
}
