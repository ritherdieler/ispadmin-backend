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
import java.util.concurrent.Executor

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
            csatSurveyService = csatSurveyService,
            inboundPipelineExecutor = Executor { command -> command.run() }
        )
        every { chatStateService.runWithPipelineState(any(), any()) } answers {
            val block = secondArg<() -> Unit>()
            block.invoke()
        }
        every { csatSurveyService.tryHandleInbound(any(), any(), any(), any(), any()) } returns false
        every { chatStateService.isBotPaused(any()) } returns false
        every { chatStateService.beginInboundInteraction(any()) } returns WhatsAppInboundSession(
            botPaused = false,
            isNewOrExpired = false,
            lastInteractionAt = LocalDateTime.now(),
            currentStep = WhatsAppConversationStep.MAIN_MENU
        )
        every { chatStateService.hasPendingInteractiveMenu(any()) } returns false
        every { conversationService.resolvePendingTextSelection(any(), any(), any()) } returns null
        every { chatStateService.getUnknownRetryCount(any()) } returns 0
        every { chatStateService.incrementUnknownRetryCount(any()) } returns 1
        every { chatStateService.resetUnknownRetryCount(any()) } returns Unit
        every { llmClient.classifyIntent(any()) } returns null
        every { whatsAppService.markMessageAsReadWithTyping(any()) } returns WhatsAppSendResult(
            success = true,
            metaResponse = """{"success":true}""",
            recipient = null,
            senderPhoneNumberId = "123"
        )
        every { handoffService.pauseBotAndPassToAdvisor(any(), any()) } returns
            WhatsAppHandoffResult(botPaused = true, metaTransferred = false)
        every { inboundMessageRepository.countByPhoneAndReadAtIsNull(any()) } returns 0L
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
            conversationService.sendSupportEntryMenu(payload.phone, null, payload.metaMessageId)
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
            conversationService.sendMainMenu(payload.phone, null, true, payload.metaMessageId)
        } returns WhatsAppConversationService.AutoReplyResult(
            success = true,
            messageText = "[MAIN_MENU] Bienvenida",
            metaMessageId = "wamid.main-menu"
        )
        val logSlot = slot<WhatsAppMessageLog>()
        every { messageLogRepository.save(capture(logSlot)) } answers { firstArg() }

        service.processInboundMessage(payload)

        verify(exactly = 1) { whatsAppService.markMessageAsReadWithTyping(payload.metaMessageId) }
        verify(exactly = 1) {
            conversationService.sendMainMenu(payload.phone, null, true, payload.metaMessageId)
        }
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
            conversationService.sendMainMenu(payload.phone, null, false, payload.metaMessageId)
        } returns WhatsAppConversationService.AutoReplyResult(
            success = true,
            messageText = "[MAIN_MENU] Selecciona una opcion para continuar",
            metaMessageId = "wamid.main-menu-active"
        )
        val logSlot = slot<WhatsAppMessageLog>()
        every { messageLogRepository.save(capture(logSlot)) } answers { firstArg() }

        service.processInboundMessage(payload)

        verify(exactly = 1) {
            conversationService.sendMainMenu(payload.phone, null, false, payload.metaMessageId)
        }
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
            conversationService.sendDebtResponseMenu(payload.phone, subscription, payload.metaMessageId)
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
    fun `unrelated text returns guardrail when interactive menu is pending`() {
        val payload = WhatsAppInboundPayload(
            metaMessageId = "wamid.in-4",
            phone = "51902354183",
            messageText = "No entiendo estas opciones",
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

        verify(exactly = 0) { conversationService.sendSupportEntryMenu(any(), any(), any()) }
        verify(exactly = 1) { conversationService.invalidInteractiveSelectionText() }
        assertTrue(logSlot.captured.message!!.contains("[MENU_INVALID]"))
        assertEquals("wamid.guardrail", logSlot.captured.metaMessageId)
    }

    @Test
    fun `new session honors technical intent instead of forcing main menu`() {
        val payload = WhatsAppInboundPayload(
            metaMessageId = "wamid.in-new-tech",
            phone = "51965754000",
            messageText = "Sin internet",
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
            if (msg.id == null) msg.copy(id = 61) else msg
        }
        every { conversationService.findSubscriptionByPhone(payload.phone) } returns null
        every { conversationService.hasRecentOperatorReply(payload.phone) } returns false
        every { conversationService.isInboundBurst(payload.phone) } returns false
        every { chatStateService.beginInboundInteraction(payload.phone) } returns WhatsAppInboundSession(
            botPaused = false,
            isNewOrExpired = true,
            lastInteractionAt = null,
            currentStep = WhatsAppConversationStep.MAIN_MENU
        )
        every {
            conversationService.sendSupportEntryMenu(payload.phone, null, payload.metaMessageId)
        } returns WhatsAppConversationService.AutoReplyResult(
            success = true,
            messageText = "[SUPPORT_MENU:SOLO_INTERNET] Selecciona el problema",
            metaMessageId = "wamid.support-new"
        )
        every { messageLogRepository.save(any()) } answers { firstArg() }

        service.processInboundMessage(payload)

        verify(exactly = 1) {
            conversationService.sendSupportEntryMenu(payload.phone, null, payload.metaMessageId)
        }
        verify(exactly = 0) { conversationService.sendMainMenu(any(), any(), any<Boolean>(), any()) }
    }

    @Test
    fun `numeric text selects pending support option`() {
        val payload = WhatsAppInboundPayload(
            metaMessageId = "wamid.in-support-number",
            phone = "51965754000",
            messageText = "1",
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
            if (msg.id == null) msg.copy(id = 62) else msg
        }
        every { conversationService.findSubscriptionByPhone(payload.phone) } returns null
        every { conversationService.hasRecentOperatorReply(payload.phone) } returns false
        every { conversationService.isInboundBurst(payload.phone) } returns false
        every { chatStateService.hasPendingInteractiveMenu(payload.phone) } returns true
        every {
            conversationService.resolvePendingTextSelection(payload.phone, null, payload.messageText)
        } returns "support_issue_no_internet"
        every { conversationService.isSupportEntryButton("support_issue_no_internet") } returns true
        every {
            conversationService.sendSupportDiagnosticQuestion(
                payload.phone,
                null,
                "support_issue_no_internet",
                payload.metaMessageId
            )
        } returns WhatsAppConversationService.AutoReplyResult(
            success = true,
            messageText = "[SUPPORT_DIAG:NO_INTERNET] Revisa LOS o PON",
            metaMessageId = "wamid.support-diag"
        )
        every { messageLogRepository.save(any()) } answers { firstArg() }

        service.processInboundMessage(payload)

        verify(exactly = 1) {
            conversationService.sendSupportDiagnosticQuestion(
                payload.phone,
                null,
                "support_issue_no_internet",
                payload.metaMessageId
            )
        }
    }

    @Test
    fun `image and pdf remain global payment proofs`() {
        val image = WhatsAppInboundPayload(
            metaMessageId = "wamid.in-voucher-image",
            phone = "51965754000",
            messageText = null,
            messageType = "image",
            mediaId = "media-image",
            mediaMimeType = "image/jpeg"
        )
        val pdf = WhatsAppInboundPayload(
            metaMessageId = "wamid.in-voucher-pdf",
            phone = "51965754000",
            messageText = "voucher.pdf",
            messageType = "document",
            mediaId = "media-pdf",
            mediaMimeType = "application/pdf"
        )
        every { conversationService.resolveReplyToLogId(null) } returns null
        every { mediaDownloadService.downloadAndStore("media-image", "image/jpeg") } returns "/tmp/image.jpg"
        every { mediaDownloadService.downloadAndStore("media-pdf", "application/pdf") } returns "/tmp/voucher.pdf"
        every { inboundMessageRepository.save(any()) } answers {
            val msg = firstArg<WhatsAppInboundMessage>()
            if (msg.id == null) msg.copy(id = if (msg.messageType == "image") 63 else 64) else msg
        }
        every { conversationService.findSubscriptionByPhone(image.phone) } returns null
        every { chatStateService.beginInboundInteraction(image.phone) } returns WhatsAppInboundSession(
            botPaused = true,
            isNewOrExpired = false,
            lastInteractionAt = LocalDateTime.now(),
            currentStep = WhatsAppConversationStep.ESPERANDO_ASESOR
        )
        every { conversationService.hasRecentOperatorReply(image.phone) } returns false
        every { conversationService.buildVoucherReceivedResponse() } returns "Comprobante recibido"
        every {
            whatsAppService.sendTextMessage(image.phone, "Comprobante recibido")
        } returns WhatsAppSendResult(
            success = true,
            metaResponse = "{}",
            metaMessageId = "wamid.voucher-ack",
            recipient = image.phone,
            senderPhoneNumberId = "123"
        )
        every { messageLogRepository.save(any()) } answers { firstArg() }

        service.processInboundMessage(image)
        service.processInboundMessage(pdf)

        verify(exactly = 2) { conversationService.buildVoucherReceivedResponse() }
        verify(exactly = 2) { whatsAppService.sendTextMessage(image.phone, "Comprobante recibido") }
    }

    @Test
    fun `payment proof button asks for image or pdf and waits`() {
        val payload = WhatsAppInboundPayload(
            metaMessageId = "wamid.in-proof",
            phone = "51965754000",
            messageText = null,
            messageType = "button_reply",
            buttonReplyId = WhatsAppConversationService.BUTTON_PAYMENT_PROOF,
            buttonReplyTitle = "Registrar pago",
            mediaId = null,
            mediaMimeType = null,
            contextMessageId = null
        )
        every { conversationService.resolveReplyToLogId(null) } returns null
        every { inboundMessageRepository.save(any()) } answers {
            val msg = firstArg<WhatsAppInboundMessage>()
            if (msg.id == null) msg.copy(id = 71) else msg
        }
        every { conversationService.findSubscriptionByPhone(payload.phone) } returns null
        every { conversationService.hasRecentOperatorReply(payload.phone) } returns false
        every {
            conversationService.sendPaymentProofRequest(payload.phone, null, payload.metaMessageId)
        } returns WhatsAppConversationService.AutoReplyResult(
            success = true,
            messageText = "[COMPROBANTE] Envíanos tu comprobante como foto o PDF",
            metaMessageId = "wamid.proof-ask"
        )
        val logSlot = slot<WhatsAppMessageLog>()
        every { messageLogRepository.save(capture(logSlot)) } answers { firstArg() }

        service.processInboundMessage(payload)

        verify(exactly = 1) {
            conversationService.sendPaymentProofRequest(payload.phone, null, payload.metaMessageId)
        }
        verify(exactly = 0) { handoffService.pauseBotAndPassToAdvisor(any(), any()) }
        assertTrue(logSlot.captured.message!!.contains("[COMPROBANTE]"))
    }

    @Test
    fun `proof received while waiting confirms and moves to receipt review`() {
        val payload = WhatsAppInboundPayload(
            metaMessageId = "wamid.in-proof-pdf",
            phone = "51965754000",
            messageText = "voucher.pdf",
            messageType = "document",
            mediaId = "media-proof",
            mediaMimeType = "application/pdf"
        )
        every { conversationService.resolveReplyToLogId(null) } returns null
        every { mediaDownloadService.downloadAndStore("media-proof", "application/pdf") } returns "/tmp/p.pdf"
        every { inboundMessageRepository.save(any()) } answers {
            val msg = firstArg<WhatsAppInboundMessage>()
            if (msg.id == null) msg.copy(id = 72) else msg
        }
        every { conversationService.findSubscriptionByPhone(payload.phone) } returns null
        every { chatStateService.beginInboundInteraction(payload.phone) } returns WhatsAppInboundSession(
            botPaused = false,
            isNewOrExpired = false,
            lastInteractionAt = LocalDateTime.now(),
            currentStep = WhatsAppConversationStep.AWAITING_PAYMENT_PROOF
        )
        every { conversationService.hasRecentOperatorReply(payload.phone) } returns false
        every { conversationService.buildVoucherReceivedResponse() } returns "Comprobante recibido"
        every { chatStateService.setCurrentStep(payload.phone, any()) } returns mockk(relaxed = true)
        every {
            whatsAppService.sendTextMessage(payload.phone, "Comprobante recibido")
        } returns WhatsAppSendResult(
            success = true,
            metaResponse = "{}",
            metaMessageId = "wamid.proof-ack",
            recipient = payload.phone,
            senderPhoneNumberId = "123"
        )
        every { messageLogRepository.save(any()) } answers { firstArg() }

        service.processInboundMessage(payload)

        verify(exactly = 1) { whatsAppService.sendTextMessage(payload.phone, "Comprobante recibido") }
        verify(exactly = 1) {
            chatStateService.setCurrentStep(payload.phone, WhatsAppConversationStep.AWAITING_RECEIPT_REVIEW)
        }
    }

    @Test
    fun `text after voucher soft acks without menu invalid`() {
        val payload = WhatsAppInboundPayload(
            metaMessageId = "wamid.in-after-voucher",
            phone = "51965754000",
            messageText = "Muchas gracias",
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
            if (msg.id == null) msg.copy(id = 74) else msg
        }
        every { conversationService.findSubscriptionByPhone(payload.phone) } returns null
        every { conversationService.hasRecentOperatorReply(payload.phone) } returns false
        every { conversationService.isInboundBurst(payload.phone) } returns false
        every { chatStateService.beginInboundInteraction(payload.phone) } returns WhatsAppInboundSession(
            botPaused = false,
            isNewOrExpired = false,
            lastInteractionAt = LocalDateTime.now(),
            currentStep = WhatsAppConversationStep.AWAITING_RECEIPT_REVIEW
        )
        every { chatStateService.hasPendingInteractiveMenu(payload.phone) } returns false
        every { conversationService.buildReceiptPendingAckResponse() } returns
            "Ya tenemos su comprobante en revision. Un asesor le confirmara en breve. Gracias."
        every {
            whatsAppService.sendTextMessage(
                payload.phone,
                "Ya tenemos su comprobante en revision. Un asesor le confirmara en breve. Gracias."
            )
        } returns WhatsAppSendResult(
            success = true,
            metaResponse = "{}",
            metaMessageId = "wamid.voucher-pending",
            recipient = payload.phone,
            senderPhoneNumberId = "123"
        )
        val logSlot = slot<WhatsAppMessageLog>()
        every { messageLogRepository.save(capture(logSlot)) } answers { firstArg() }

        service.processInboundMessage(payload)

        verify(exactly = 1) { conversationService.buildReceiptPendingAckResponse() }
        verify(exactly = 0) { conversationService.invalidInteractiveSelectionText() }
        verify(exactly = 0) { conversationService.sendMainMenu(any(), any(), any(), any()) }
        assertTrue(logSlot.captured.message!!.contains("[VOUCHER_PENDING]"))
    }

    @Test
    fun `unknown text after voucher soft acks without opening main menu`() {
        val payload = WhatsAppInboundPayload(
            metaMessageId = "wamid.in-after-voucher-unknown",
            phone = "51965754000",
            messageText = "Por favor me manda mi boleta como siempre",
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
            if (msg.id == null) msg.copy(id = 75) else msg
        }
        every { conversationService.findSubscriptionByPhone(payload.phone) } returns null
        every { conversationService.hasRecentOperatorReply(payload.phone) } returns false
        every { conversationService.isInboundBurst(payload.phone) } returns false
        every { chatStateService.beginInboundInteraction(payload.phone) } returns WhatsAppInboundSession(
            botPaused = false,
            isNewOrExpired = false,
            lastInteractionAt = LocalDateTime.now(),
            currentStep = WhatsAppConversationStep.AWAITING_RECEIPT_REVIEW
        )
        every { chatStateService.hasPendingInteractiveMenu(payload.phone) } returns false
        every { conversationService.buildReceiptPendingAckResponse() } returns
            "Ya tenemos su comprobante en revision. Un asesor le confirmara en breve. Gracias."
        every {
            whatsAppService.sendTextMessage(
                payload.phone,
                "Ya tenemos su comprobante en revision. Un asesor le confirmara en breve. Gracias."
            )
        } returns WhatsAppSendResult(
            success = true,
            metaResponse = "{}",
            metaMessageId = "wamid.voucher-pending-2",
            recipient = payload.phone,
            senderPhoneNumberId = "123"
        )
        val logSlot = slot<WhatsAppMessageLog>()
        every { messageLogRepository.save(capture(logSlot)) } answers { firstArg() }

        service.processInboundMessage(payload)

        verify(exactly = 1) { conversationService.buildReceiptPendingAckResponse() }
        verify(exactly = 0) { conversationService.sendMainMenu(any(), any(), any(), any()) }
        assertTrue(logSlot.captured.message!!.contains("[VOUCHER_PENDING]"))
    }

    @Test
    fun `text while waiting for proof reminds instead of generic guardrail`() {
        val payload = WhatsAppInboundPayload(
            metaMessageId = "wamid.in-proof-text",
            phone = "51965754000",
            messageText = "ahi va",
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
            if (msg.id == null) msg.copy(id = 73) else msg
        }
        every { conversationService.findSubscriptionByPhone(payload.phone) } returns null
        every { conversationService.hasRecentOperatorReply(payload.phone) } returns false
        every { conversationService.isInboundBurst(payload.phone) } returns false
        every { chatStateService.beginInboundInteraction(payload.phone) } returns WhatsAppInboundSession(
            botPaused = false,
            isNewOrExpired = false,
            lastInteractionAt = LocalDateTime.now(),
            currentStep = WhatsAppConversationStep.AWAITING_PAYMENT_PROOF
        )
        every { chatStateService.hasPendingInteractiveMenu(payload.phone) } returns true
        every {
            conversationService.resolvePendingTextSelection(payload.phone, null, payload.messageText)
        } returns null
        every { conversationService.buildPaymentProofReminder() } returns "Seguimos esperando tu comprobante"
        every {
            whatsAppService.sendTextMessage(payload.phone, "Seguimos esperando tu comprobante")
        } returns WhatsAppSendResult(
            success = true,
            metaResponse = "{}",
            metaMessageId = "wamid.proof-remind",
            recipient = payload.phone,
            senderPhoneNumberId = "123"
        )
        val logSlot = slot<WhatsAppMessageLog>()
        every { messageLogRepository.save(capture(logSlot)) } answers { firstArg() }

        service.processInboundMessage(payload)

        verify(exactly = 1) { conversationService.buildPaymentProofReminder() }
        verify(exactly = 0) { conversationService.invalidInteractiveSelectionText() }
        assertTrue(logSlot.captured.message!!.contains("[COMPROBANTE_PENDIENTE]"))
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
            conversationService.sendSupportEntryMenu(payload.phone, null, payload.metaMessageId)
        } returns WhatsAppConversationService.AutoReplyResult(
            success = true,
            messageText = "[SUPPORT_MENU:SOLO_INTERNET] Selecciona el problema",
            metaMessageId = "wamid.burst-support"
        )
        val logSlot = slot<WhatsAppMessageLog>()
        every { messageLogRepository.save(capture(logSlot)) } answers { firstArg() }

        service.processInboundMessage(payload)

        verify(exactly = 1) {
            conversationService.sendSupportEntryMenu(payload.phone, null, payload.metaMessageId)
        }
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
        every { inboundMessageRepository.countByPhoneAndReadAtIsNull(payload.phone) } returns 1L

        service.processInboundMessage(payload)

        verify {
            crmEventPublisher.publish(
                CrmEventPublisher.MESSAGE_RECEIVED,
                match {
                    it["phone"] == payload.phone &&
                        it["inboundMessageId"] == 99 &&
                        it.containsKey("mediaMimeType")
                }
            )
        }
        verify {
            crmEventPublisher.publish(
                CrmEventPublisher.CONVERSATION_UPDATED,
                match { it["phone"] == payload.phone && it["unreadCount"] == 1 }
            )
        }
    }

    @Test
    fun `auto resumed advisor wait sends main menu and syncs handoff`() {
        val payload = WhatsAppInboundPayload(
            metaMessageId = "wamid.in-auto-resume",
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
            if (msg.id == null) msg.copy(id = 77) else msg
        }
        every { conversationService.findSubscriptionByPhone(payload.phone) } returns null
        every { conversationService.hasRecentOperatorReply(payload.phone) } returns false
        every { conversationService.isInboundBurst(payload.phone) } returns false
        every { chatStateService.beginInboundInteraction(payload.phone) } returns WhatsAppInboundSession(
            botPaused = false,
            isNewOrExpired = true,
            lastInteractionAt = LocalDateTime.now().minusHours(3),
            currentStep = WhatsAppConversationStep.MAIN_MENU,
            autoResumedFromAdvisorWait = true
        )
        every {
            handoffService.resumeBotAndTakeControl(payload.phone, "auto_resume_advisor_wait")
        } returns WhatsAppHandoffResult(botPaused = false, metaTransferred = false)
        every {
            conversationService.sendMainMenu(payload.phone, null, true, payload.metaMessageId)
        } returns WhatsAppConversationService.AutoReplyResult(
            success = true,
            messageText = "[MAIN_MENU] Bienvenida",
            metaMessageId = "wamid.main-menu-auto"
        )
        every { messageLogRepository.save(any()) } answers { firstArg() }

        service.processInboundMessage(payload)

        verify(exactly = 1) {
            handoffService.resumeBotAndTakeControl(payload.phone, "auto_resume_advisor_wait")
        }
        verify(exactly = 1) {
            conversationService.sendMainMenu(payload.phone, null, true, payload.metaMessageId)
        }
    }

    @Test
    fun `processInboundReaction updates outbound emoji and never inserts inbound`() {
        val outbound = WhatsAppMessageLog(
            id = 55,
            phone = "51902354183",
            metaMessageId = "wamid.OUT.55",
            messageType = "OPERATOR_REPLY",
            status = "SENT",
            message = "Hola",
        )
        every { messageLogRepository.findByMetaMessageId("wamid.OUT.55") } returns outbound
        every { messageLogRepository.save(any()) } answers { firstArg() }

        service.processInboundReaction(
            WhatsAppInboundPayload(
                metaMessageId = "wamid.reaction.1",
                phone = "51902354183",
                messageType = "reaction",
                messageText = "😂",
                reactionMessageId = "wamid.OUT.55",
                reactionEmoji = "😂",
            )
        )

        assertEquals("😂", outbound.customerReactionEmoji)
        verify(exactly = 1) { messageLogRepository.save(outbound) }
        verify(exactly = 0) { inboundMessageRepository.save(any()) }
        verify {
            crmEventPublisher.publish(
                CrmEventPublisher.MESSAGE_REACTION,
                match {
                    it["threadMessageId"] == "outbound:55" &&
                        it["emoji"] == "😂" &&
                        it["metaMessageId"] == "wamid.OUT.55"
                }
            )
        }
    }

    @Test
    fun `processInboundMessage with reaction type never inserts inbound row`() {
        val outbound = WhatsAppMessageLog(
            id = 77,
            phone = "51902354183",
            metaMessageId = "wamid.OUT.77",
            messageType = "OPERATOR_REPLY",
            status = "SENT",
            message = "Hola",
        )
        every { messageLogRepository.findByMetaMessageId("wamid.OUT.77") } returns outbound
        every { messageLogRepository.save(any()) } answers { firstArg() }

        service.processInboundMessage(
            WhatsAppInboundPayload(
                metaMessageId = "wamid.reaction.guard",
                phone = "51902354183",
                messageType = "reaction",
                messageText = null,
                reactionMessageId = "wamid.OUT.77",
                reactionEmoji = "👍",
            )
        )

        assertEquals("👍", outbound.customerReactionEmoji)
        verify(exactly = 0) { inboundMessageRepository.save(any()) }
        verify(exactly = 1) { messageLogRepository.save(outbound) }
    }

    @Test
    fun `processInboundReaction discards when target wamid missing without insert`() {
        every { messageLogRepository.findByMetaMessageId("wamid.MISSING") } returns null
        every { inboundMessageRepository.findByMetaMessageId("wamid.MISSING") } returns null

        service.processInboundReaction(
            WhatsAppInboundPayload(
                metaMessageId = "wamid.reaction.orphan",
                phone = "51902354183",
                messageType = "reaction",
                messageText = null,
                reactionMessageId = "wamid.MISSING",
                reactionEmoji = "🔥",
            )
        )

        verify(exactly = 0) { inboundMessageRepository.save(any()) }
        verify(exactly = 0) { messageLogRepository.save(any()) }
        verify(exactly = 0) { crmEventPublisher.publish(any(), any()) }
    }
}
