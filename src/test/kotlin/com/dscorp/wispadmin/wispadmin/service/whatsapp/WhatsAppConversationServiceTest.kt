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
    private val crmConversationService = mockk<CrmConversationService>(relaxed = true)
    private val mediaDownloadService = mockk<WhatsAppMediaDownloadService>(relaxed = true)
    private val templateDeliveryService = mockk<WhatsAppTemplateDeliveryService>()
    private val templateDisplayService = mockk<WhatsAppTemplateDisplayService>(relaxed = true)
    private val whatsAppProperties = WhatsAppProperties().apply {
        autoReply = WhatsAppAutoReplyProperties()
    }
    private val userRepository = mockk<com.dscorp.wispadmin.wispadmin.repository.UserRepository>(relaxed = true)
    private val operatorDisplayNameResolver = WhatsAppOperatorDisplayNameResolver(userRepository)

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
            chatStateService = chatStateService,
            crmConversationService = crmConversationService,
            mediaDownloadService = mediaDownloadService,
            templateDeliveryService = templateDeliveryService,
            templateDisplayService = templateDisplayService,
            operatorDisplayNameResolver = operatorDisplayNameResolver,
            crmEventPublisher = mockk(relaxed = true),
        )
        every { templateDisplayService.displayStoredMessage(any(), any()) } answers { firstArg() }
        every { chatStateService.currentStep(any()) } returns null
        every { chatStateService.hasPendingSupportDiagnostic(any()) } returns false
        every { chatStateService.hasPendingInteractiveMenu(any()) } returns false
        every { chatStateService.setCurrentStep(any(), any()) } answers {
            WhatsAppChatState(
                phone = firstArg(),
                currentStep = secondArg()
            )
        }
        every { chatStateService.markWaitingForAdvisor(any(), any()) } answers {
            WhatsAppChatState(
                phone = firstArg(),
                currentStep = WhatsAppConversationStep.ESPERANDO_ASESOR,
                botPaused = true
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
        assertTrue(response.contains("ya está registrado"))
        assertTrue(response.contains("al día"))
        assertTrue(response.contains("Gracias por su puntualidad"))
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
        assertTrue(response.contains("caso quedó registrado"))
        assertTrue(
            response.contains("le atiende una persona") || response.contains("primera hora")
        )
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
            whatsAppService.sendTextMessage(phone, "Hola cliente", null)
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

        val result = service.sendOperatorReply(
            phone = phone,
            text = "Hola cliente",
            operatorUsername = "operador1",
            agentId = 7,
            isAdmin = false
        )

        assertEquals("outbound:55", result.id)
        assertEquals("OUTBOUND", result.direction)
        assertEquals("OPERATOR_REPLY", result.messageType)
        assertEquals("operador1", result.operatorUsername)
        assertEquals("Hola cliente", result.body)
        assertEquals("OPERATOR_REPLY", savedSlot.captured.messageType)
        assertEquals("SENT", savedSlot.captured.status)
        assertEquals("wamid.op-1", savedSlot.captured.metaMessageId)
        verify { crmConversationService.assertCanReply(phone, 7, false) }
        verify { chatStateService.markWaitingForAdvisor(phone, "operator_reply") }
        verify { crmConversationService.touchOutbound(phone) }
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
        verify(exactly = 0) { whatsAppService.sendTextMessage(any(), any(), any()) }
        verify(exactly = 0) { messageLogRepository.save(any()) }
    }

    @Test
    fun `sendOperatorReply with reply-to sends context message id`() {
        val phone = "51902354183"
        every { serviceWindowService.getServiceWindow(phone) } returns
            WhatsAppServiceWindowService.WhatsAppServiceWindowStatus(
                phone = phone,
                open = true,
                expiresAt = LocalDateTime.now().plusHours(2)
            )
        every { subscriptionRepository.findByNormalizedPhone("902354183") } returns emptyList()
        every { inboundMessageRepository.findById(12) } returns java.util.Optional.of(
            WhatsAppInboundMessage(
                id = 12,
                metaMessageId = "wamid.context-12",
                phone = phone,
                messageText = "Hola"
            )
        )
        every {
            whatsAppService.sendTextMessage(phone, "Respuesta", "wamid.context-12")
        } returns WhatsAppSendResult(
            success = true,
            metaResponse = "{}",
            metaMessageId = "wamid.op-2",
            recipient = phone,
            senderPhoneNumberId = "123"
        )
        val savedSlot = slot<WhatsAppMessageLog>()
        every { messageLogRepository.save(capture(savedSlot)) } answers {
            firstArg<WhatsAppMessageLog>().copy(id = 77)
        }

        val result = service.sendOperatorReply(
            phone = phone,
            text = "Respuesta",
            operatorUsername = "operador1",
            agentId = 7,
            replyToMessageId = "inbound:12"
        )

        assertEquals("outbound:77", result.id)
        assertEquals(12, savedSlot.captured.replyToLogId)
        verify { whatsAppService.sendTextMessage(phone, "Respuesta", "wamid.context-12") }
    }

    @Test
    fun `sendOperatorMedia uploads validates and persists outbound media`() {
        val phone = "51902354183"
        val bytes = ByteArray(1024) { 1 }
        every { serviceWindowService.getServiceWindow(phone) } returns
            WhatsAppServiceWindowService.WhatsAppServiceWindowStatus(
                phone = phone,
                open = true,
                expiresAt = LocalDateTime.now().plusHours(1)
            )
        every { subscriptionRepository.findByNormalizedPhone("902354183") } returns emptyList()
        every {
            mediaDownloadService.storeOutboundBytes(bytes, "image/jpeg", any())
        } returns "/tmp/out.jpg"
        every { whatsAppService.uploadMedia(bytes, "image/jpeg", any()) } returns "meta-media-1"
        every {
            whatsAppService.sendMediaMessage(
                phoneNumber = phone,
                kind = WhatsAppOutboundMediaKind.IMAGE,
                mediaId = "meta-media-1",
                caption = "Voucher",
                filename = any(),
                contextMessageId = null
            )
        } returns WhatsAppSendResult(
            success = true,
            metaResponse = "{}",
            metaMessageId = "wamid.media-1",
            recipient = phone,
            senderPhoneNumberId = "123"
        )
        val savedSlot = slot<WhatsAppMessageLog>()
        every { messageLogRepository.save(capture(savedSlot)) } answers {
            firstArg<WhatsAppMessageLog>().copy(id = 88)
        }

        val result = service.sendOperatorMedia(
            phone = phone,
            bytes = bytes,
            mimeType = "image/jpeg",
            filename = "voucher.jpg",
            caption = "Voucher",
            operatorUsername = "operador1",
            agentId = 7
        )

        assertEquals("OPERATOR_MEDIA", result.messageType)
        assertEquals("meta-media-1", savedSlot.captured.mediaMetaId)
        assertEquals("/tmp/out.jpg", savedSlot.captured.mediaStoredPath)
        assertTrue(result.hasMedia)
        verify { crmConversationService.assertCanReply(phone, 7, false) }
    }

    @Test
    fun `sendOperatorMedia rejects when not assignee`() {
        val phone = "51902354183"
        every {
            crmConversationService.assertCanReply(phone, 9, false)
        } throws CrmConversationForbiddenException("No eres el agente asignado.")

        assertThrows(CrmConversationForbiddenException::class.java) {
            service.sendOperatorMedia(
                phone = phone,
                bytes = byteArrayOf(1, 2, 3),
                mimeType = "image/png",
                filename = "a.png",
                caption = null,
                operatorUsername = "otro",
                agentId = 9
            )
        }
        verify(exactly = 0) { whatsAppService.uploadMedia(any(), any(), any()) }
    }

    @Test
    fun `sendOperatorTemplate requires ownership and uses delivery service`() {
        val phone = "51902354183"
        val subscription = Subscription(
            firstName = "Ana",
            lastName = "Lopez",
            phone = "902354183",
            serviceStatus = ServiceStatus.ACTIVE,
            equipmentCondition = EquipmentCondition.LOAN
        ).apply { id = 10 }
        every { subscriptionRepository.findByNormalizedPhone("902354183") } returns listOf(subscription)
        every {
            paymentRepository.findUnpaidBySubscriptionIdOrderByBillingDateDatetimeAsc(10)
        } returns listOf(
            Payment(
                discountAmount = 0.0,
                paid = false,
                amountToPay = 50.0,
                billingDateDatetime = LocalDateTime.of(2026, 5, 31, 0, 0)
            ).apply { id = 99 }
        )
        every {
            templateDeliveryService.deliverTemplate(
                definition = any(),
                subscription = subscription,
                phone = phone,
                payment = any(),
                oldestUnpaidPayment = any(),
                paymentId = 99,
                subscriptionId = 10,
                welcomeContext = null,
                operatorUsername = "operador1"
            )
        } returns WhatsAppMessageLog(
            id = 101,
            phone = phone,
            messageType = "PAYMENT_REMINDER",
            status = "SENT",
            message = "preview",
            operatorUsername = "operador1"
        )

        val result = service.sendOperatorTemplate(
            phone = phone,
            templateCode = "PAYMENT_REMINDER",
            operatorUsername = "operador1",
            agentId = 7,
            isAdmin = true
        )

        assertEquals("outbound:101", result.id)
        assertEquals("PAYMENT_REMINDER", result.templateCode)
        verify { crmConversationService.assertCanReply(phone, 7, true) }
        verify { chatStateService.markWaitingForAdvisor(phone, "operator_template") }
    }

    @Test
    fun `sendOperatorTemplate allows non-admin assignee when assertCanReply passes`() {
        val phone = "51902354183"
        val subscription = Subscription(
            firstName = "Ana",
            lastName = "Lopez",
            phone = "902354183",
            serviceStatus = ServiceStatus.ACTIVE,
            equipmentCondition = EquipmentCondition.LOAN
        ).apply { id = 10 }
        every { subscriptionRepository.findByNormalizedPhone("902354183") } returns listOf(subscription)
        every {
            paymentRepository.findUnpaidBySubscriptionIdOrderByBillingDateDatetimeAsc(10)
        } returns listOf(
            Payment(
                discountAmount = 0.0,
                paid = false,
                amountToPay = 50.0,
                billingDateDatetime = LocalDateTime.of(2026, 5, 31, 0, 0)
            ).apply { id = 99 }
        )
        every {
            templateDeliveryService.deliverTemplate(
                definition = any(),
                subscription = subscription,
                phone = phone,
                payment = any(),
                oldestUnpaidPayment = any(),
                paymentId = 99,
                subscriptionId = 10,
                welcomeContext = null,
                operatorUsername = "secretaria1"
            )
        } returns WhatsAppMessageLog(
            id = 102,
            phone = phone,
            messageType = "PAYMENT_REMINDER",
            status = "SENT",
            message = "preview",
            operatorUsername = "secretaria1"
        )

        val result = service.sendOperatorTemplate(
            phone = phone,
            templateCode = "PAYMENT_REMINDER",
            operatorUsername = "secretaria1",
            agentId = 7,
            isAdmin = false
        )

        assertEquals("outbound:102", result.id)
        verify { crmConversationService.assertCanReply(phone, 7, false) }
    }

    @Test
    fun `sendOperatorTemplate forbidden when assertCanReply rejects non-assignee`() {
        every {
            crmConversationService.assertCanReply("51902354183", 7, false)
        } throws CrmConversationForbiddenException("Debes tomar la conversacion antes de responder")

        val ex = assertThrows(CrmConversationForbiddenException::class.java) {
            service.sendOperatorTemplate(
                phone = "51902354183",
                templateCode = "PAYMENT_REMINDER",
                operatorUsername = "operador1",
                agentId = 7,
                isAdmin = false
            )
        }
        assertEquals("Debes tomar la conversacion antes de responder", ex.message)
        verify(exactly = 0) {
            templateDeliveryService.deliverTemplate(
                definition = any(),
                subscription = any(),
                phone = any(),
                payment = any(),
                oldestUnpaidPayment = any(),
                paymentId = any(),
                subscriptionId = any(),
                welcomeContext = any(),
                operatorUsername = any()
            )
        }
    }

    @Test
    fun `retryFailedOutbound rejects when max retries reached`() {
        val phone = "51902354183"
        every { messageLogRepository.findById(5) } returns java.util.Optional.of(
            WhatsAppMessageLog(
                id = 5,
                phone = phone,
                status = "FAILED",
                retryCount = WhatsAppMediaConstraints.MAX_RETRY_COUNT,
                message = "hola"
            )
        )

        val ex = assertThrows(IllegalArgumentException::class.java) {
            service.retryFailedOutbound(phone, 5, "operador1", agentId = 7)
        }
        assertTrue(ex.message!!.contains("maximo", ignoreCase = true))
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
            WhatsAppInboundMessage(id = 1, metaMessageId = "m1", phone = phone, readAt = null, createdAt = LocalDateTime.now().minusMinutes(5)),
            WhatsAppInboundMessage(id = 2, metaMessageId = "m2", phone = phone, readAt = null, createdAt = LocalDateTime.now())
        )
        every { inboundMessageRepository.findByPhoneAndReadAtIsNull(any()) } answers {
            val queried = firstArg<String>()
            if (queried == phone || queried == "902354183") unread else emptyList()
        }
        every { whatsAppService.markMessageAsRead(any()) } returns WhatsAppSendResult(
            success = true,
            metaResponse = "{}",
            metaMessageId = null,
            recipient = null,
            senderPhoneNumberId = "123"
        )
        every { inboundMessageRepository.markReadByIds(any(), any()) } returns 2

        val result = service.markAllRead(phone)

        assertEquals(2, result.markedCount)
        assertTrue(result.success)
        verify(exactly = 1) { inboundMessageRepository.markReadByIds(match { it.toSet() == setOf(1, 2) }, any()) }
        verify(exactly = 0) { inboundMessageRepository.save(any()) }
    }

    @Test
    fun `markAllRead llama markMessageAsRead una sola vez para el mensaje mas reciente`() {
        val phone = "51902354183"
        val unread = listOf(
            WhatsAppInboundMessage(id = 1, metaMessageId = "m1", phone = phone, readAt = null, createdAt = LocalDateTime.now().minusMinutes(5)),
            WhatsAppInboundMessage(id = 2, metaMessageId = "m2", phone = phone, readAt = null, createdAt = LocalDateTime.now())
        )
        every { inboundMessageRepository.findByPhoneAndReadAtIsNull(any()) } answers {
            val queried = firstArg<String>()
            if (queried == phone || queried == "902354183") unread else emptyList()
        }
        every { whatsAppService.markMessageAsRead(any()) } returns WhatsAppSendResult(
            success = true,
            metaResponse = "{}",
            metaMessageId = null,
            recipient = null,
            senderPhoneNumberId = "123"
        )
        every { inboundMessageRepository.markReadByIds(any(), any()) } returns 2

        service.markAllRead(phone)

        verify(exactly = 1) { whatsAppService.markMessageAsRead(any()) }
        verify(exactly = 1) { whatsAppService.markMessageAsRead("m2") }
    }

    @Test
    fun `markAllRead no hace nada si no hay mensajes no leidos`() {
        val phone = "51902354183"
        every { inboundMessageRepository.findByPhoneAndReadAtIsNull(any()) } returns emptyList()

        val result = service.markAllRead(phone)

        assertEquals(0, result.markedCount)
        assertTrue(result.success)
        verify(exactly = 0) { whatsAppService.markMessageAsRead(any()) }
        verify(exactly = 0) { inboundMessageRepository.markReadByIds(any(), any()) }
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
    fun `hasRecentVoucherAck looks for recent VOUCHER auto reply`() {
        every {
            messageLogRepository.existsByPhoneAndMessageTypeAndMessageStartingWithAndCreatedAtAfter(
                phone = "51913075891",
                messageType = WhatsAppConversationService.MESSAGE_TYPE_AUTO_REPLY,
                message = WhatsAppConversationService.VOUCHER_ACK_MARKER,
                createdAt = any()
            )
        } returns true

        assertTrue(service.hasRecentVoucherAck("51913075891"))
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

    @Test
    fun `main menu offers payment proof option with descriptions`() {
        val rowsSlot = slot<List<WhatsAppService.InteractiveListOption>>()
        every {
            whatsAppService.sendInteractiveListMessage(
                phoneNumber = "51902354183",
                bodyText = any(),
                buttonText = any(),
                sectionTitle = any(),
                rows = capture(rowsSlot),
                footerText = "Puede escribir MENU o ASESOR en cualquier momento",
                contextMessageId = "wamid.in-menu"
            )
        } returns WhatsAppSendResult(
            success = true,
            metaResponse = "{}",
            metaMessageId = "wamid.menu",
            recipient = "51902354183",
            senderPhoneNumberId = "123"
        )

        val result = service.sendMainMenu(
            phone = "51902354183",
            subscription = null,
            includeGreeting = true,
            contextMessageId = "wamid.in-menu"
        )

        assertTrue(result.success)
        assertEquals(
            listOf("reportar_averia", "ver_deuda", "enviar_comprobante", "hablar_asesor"),
            rowsSlot.captured.map { it.id }
        )
        assertEquals("Registrar pago", rowsSlot.captured[2].title)
        assertTrue(rowsSlot.captured.all { !it.description.isNullOrBlank() })
        assertTrue(rowsSlot.captured.none { it.id == WhatsAppConversationService.BUTTON_INSTALLATION })
        verify { chatStateService.setCurrentStep("51902354183", WhatsAppConversationStep.MAIN_MENU) }
    }

    @Test
    fun `payment proof request waits for image or pdf`() {
        val buttonsSlot = slot<List<WhatsAppService.InteractiveButtonOption>>()
        every {
            whatsAppService.sendInteractiveReplyButtons(
                phoneNumber = "51902354183",
                bodyText = any(),
                buttons = capture(buttonsSlot),
                footerText = "Puede enviar su comprobante como imagen o PDF",
                contextMessageId = "wamid.in-proof"
            )
        } returns WhatsAppSendResult(
            success = true,
            metaResponse = "{}",
            metaMessageId = "wamid.proof",
            recipient = "51902354183",
            senderPhoneNumberId = "123"
        )

        val result = service.sendPaymentProofRequest(
            phone = "51902354183",
            subscription = null,
            contextMessageId = "wamid.in-proof"
        )

        assertTrue(result.success)
        assertTrue(result.messageText.contains("[COMPROBANTE]"))
        assertTrue(result.messageText.contains("PDF"))
        assertEquals(listOf("nav_home", "hablar_asesor"), buttonsSlot.captured.map { it.id })
        verify {
            chatStateService.setCurrentStep(
                "51902354183",
                WhatsAppConversationStep.AWAITING_PAYMENT_PROOF
            )
        }
    }

    @Test
    fun `paid status menu shows pending amount and waits for proof`() {
        val subscription = Subscription(
            firstName = "Ana",
            lastName = "Lopez",
            phone = "902354183",
            serviceStatus = ServiceStatus.ACTIVE,
            equipmentCondition = EquipmentCondition.LOAN
        ).apply { id = 31 }
        every {
            paymentRepository.findUnpaidBySubscriptionIdOrderByBillingDateDatetimeAsc(31)
        } returns listOf(
            Payment(
                discountAmount = 0.0,
                paid = false,
                amountToPay = 50.0,
                billingDateDatetime = LocalDateTime.of(2026, 5, 31, 0, 0)
            )
        )
        val bodySlot = slot<String>()
        every {
            whatsAppService.sendInteractiveReplyButtons(
                phoneNumber = "51902354183",
                bodyText = capture(bodySlot),
                buttons = any(),
                footerText = "Puede enviar su comprobante como imagen o PDF",
                contextMessageId = null
            )
        } returns WhatsAppSendResult(
            success = true,
            metaResponse = "{}",
            metaMessageId = "wamid.paid",
            recipient = "51902354183",
            senderPhoneNumberId = "123"
        )

        val result = service.sendPaidStatusMenu(
            phone = "51902354183",
            subscription = subscription,
            contextMessageId = null
        )

        assertTrue(result.success)
        assertTrue(bodySlot.captured.contains("50.00"))
        verify {
            chatStateService.setCurrentStep(
                "51902354183",
                WhatsAppConversationStep.AWAITING_PAYMENT_PROOF
            )
        }
    }

    @Test
    fun `pending text selection works while waiting for payment proof`() {
        val phone = "51902354183"
        every { chatStateService.currentStep(phone) } returns
            WhatsAppConversationStep.AWAITING_PAYMENT_PROOF

        assertEquals("nav_home", service.resolvePendingTextSelection(phone, null, "1"))
        assertEquals(
            "hablar_asesor",
            service.resolvePendingTextSelection(phone, null, "hablar con asesor")
        )
    }

    @Test
    fun `debt response offers paid home and advisor actions`() {
        val buttonsSlot = slot<List<WhatsAppService.InteractiveButtonOption>>()
        every {
            whatsAppService.sendInteractiveReplyButtons(
                phoneNumber = "51902354183",
                bodyText = any(),
                buttons = capture(buttonsSlot),
                footerText = "Puede enviar su comprobante como imagen o PDF",
                contextMessageId = "wamid.in-debt"
            )
        } returns WhatsAppSendResult(
            success = true,
            metaResponse = "{}",
            metaMessageId = "wamid.debt-menu",
            recipient = "51902354183",
            senderPhoneNumberId = "123"
        )

        val result = service.sendDebtResponseMenu(
            phone = "51902354183",
            subscription = null,
            contextMessageId = "wamid.in-debt"
        )

        assertTrue(result.success)
        assertEquals(
            listOf("ya_pague", "nav_home", "hablar_asesor"),
            buttonsSlot.captured.map { it.id }
        )
    }

    @Test
    fun `support menu uses direct issue buttons and global text commands`() {
        val buttonsSlot = slot<List<WhatsAppService.InteractiveButtonOption>>()
        every {
            whatsAppService.sendInteractiveReplyButtons(
                phoneNumber = "51902354183",
                bodyText = any(),
                buttons = capture(buttonsSlot),
                footerText = "Puede escribir MENU o ASESOR en cualquier momento",
                contextMessageId = "wamid.in-support"
            )
        } returns WhatsAppSendResult(
            success = true,
            metaResponse = "{}",
            metaMessageId = "wamid.support-menu",
            recipient = "51902354183",
            senderPhoneNumberId = "123"
        )

        val result = service.sendSupportEntryMenu(
            phone = "51902354183",
            subscription = null,
            contextMessageId = "wamid.in-support"
        )

        assertTrue(result.success)
        assertEquals(
            listOf("support_issue_no_internet", "support_issue_slow_internet"),
            buttonsSlot.captured.map { it.id }
        )
        assertTrue(buttonsSlot.captured.none { it.id.startsWith("nav_") })
    }

    @Test
    fun `unknown callback does not fall through to support`() {
        val response = service.handleButtonReply(
            phone = "51902354183",
            buttonReplyId = "unknown_callback",
            subscription = null,
            sourceText = "Opción antigua"
        )

        assertEquals(service.invalidInteractiveSelectionText(), response)
    }

    @Test
    fun `reactToInboundMessage sends Meta reaction and persists agent emoji`() {
        val inbound = WhatsAppInboundMessage(
            id = 9,
            metaMessageId = "wamid.IN.9",
            phone = "51902354183",
            messageText = "Hola",
            messageType = "text",
        )
        every { inboundMessageRepository.findByMetaMessageId("wamid.IN.9") } returns inbound
        every { inboundMessageRepository.save(any()) } answers { firstArg() }
        every {
            whatsAppService.sendReaction("51902354183", "wamid.IN.9", "👍")
        } returns WhatsAppSendResult(
            success = true,
            metaResponse = "{}",
            metaMessageId = "wamid.react",
            recipient = "51902354183",
            senderPhoneNumberId = "123",
        )

        val dto = service.reactToInboundMessage(wamid = "wamid.IN.9", emoji = "👍", isAdmin = true)

        assertEquals("👍", inbound.agentReactionEmoji)
        assertEquals("👍", dto.reactionEmoji)
        assertEquals("inbound:9", dto.id)
        verify { whatsAppService.sendReaction("51902354183", "wamid.IN.9", "👍") }
    }

    @Test
    fun `editOutboundMessage soft-edits within window`() {
        val created = LocalDateTime.now().minusMinutes(5)
        val log = WhatsAppMessageLog(
            id = 3,
            phone = "51902354183",
            metaMessageId = "wamid.OUT.3",
            messageType = "OPERATOR_REPLY",
            status = "SENT",
            message = "Original",
            createdAt = created,
        )
        every { messageLogRepository.findByMetaMessageId("wamid.OUT.3") } returns log
        every { messageLogRepository.save(any()) } answers { firstArg() }

        val dto = service.editOutboundMessage(wamid = "wamid.OUT.3", text = "Editado", isAdmin = true)

        assertEquals("Original", log.originalMessage)
        assertEquals("Editado", log.message)
        assertNotNull(log.editedAt)
        assertEquals("Editado", dto.body)
        assertNotNull(dto.editedAt)
    }

    @Test
    fun `deleteOutboundMessage soft-deletes within window`() {
        val created = LocalDateTime.now().minusHours(2)
        val log = WhatsAppMessageLog(
            id = 4,
            phone = "51902354183",
            metaMessageId = "wamid.OUT.4",
            messageType = "OPERATOR_REPLY",
            status = "SENT",
            message = "Borrar",
            createdAt = created,
        )
        every { messageLogRepository.findByMetaMessageId("wamid.OUT.4") } returns log
        every { messageLogRepository.save(any()) } answers { firstArg() }

        val dto = service.deleteOutboundMessage(wamid = "wamid.OUT.4", isAdmin = true)

        assertNotNull(log.deletedAt)
        assertEquals("DELETED", dto.deliveryStatus)
        assertNotNull(dto.deletedAt)
    }

    @Test
    fun `editOutboundMessage rejects after 15 minutes`() {
        val log = WhatsAppMessageLog(
            id = 5,
            phone = "51902354183",
            metaMessageId = "wamid.OUT.5",
            messageType = "OPERATOR_REPLY",
            status = "SENT",
            message = "Viejo",
            createdAt = LocalDateTime.now().minusMinutes(20),
        )
        every { messageLogRepository.findByMetaMessageId("wamid.OUT.5") } returns log

        val ex = assertThrows(IllegalArgumentException::class.java) {
            service.editOutboundMessage(wamid = "wamid.OUT.5", text = "X", isAdmin = true)
        }
        assertEquals(WhatsAppMessageMutationPolicy.EDIT_EXPIRED_MESSAGE, ex.message)
        verify(exactly = 0) { messageLogRepository.save(any()) }
    }

    @Test
    fun `deleteOutboundMessage rejects after 24 hours`() {
        val log = WhatsAppMessageLog(
            id = 6,
            phone = "51902354183",
            metaMessageId = "wamid.OUT.6",
            messageType = "OPERATOR_REPLY",
            status = "SENT",
            message = "Antiguo",
            createdAt = LocalDateTime.now().minusHours(25),
        )
        every { messageLogRepository.findByMetaMessageId("wamid.OUT.6") } returns log

        val ex = assertThrows(IllegalArgumentException::class.java) {
            service.deleteOutboundMessage(wamid = "wamid.OUT.6", isAdmin = true)
        }
        assertEquals(WhatsAppMessageMutationPolicy.DELETE_EXPIRED_MESSAGE, ex.message)
        verify(exactly = 0) { messageLogRepository.save(any()) }
    }

    @Test
    fun `editOutboundMessage resolves by local outbound id when wamid missing`() {
        val created = LocalDateTime.now().minusMinutes(2)
        val log = WhatsAppMessageLog(
            id = 88,
            phone = "51902354183",
            metaMessageId = null,
            messageType = "OPERATOR_REPLY",
            status = "SENT",
            message = "Sin wamid",
            createdAt = created,
        )
        every { messageLogRepository.findByMetaMessageId("outbound:88") } returns null
        every { messageLogRepository.findById(88) } returns java.util.Optional.of(log)
        every { messageLogRepository.save(any()) } answers { firstArg() }

        val dto = service.editOutboundMessage(wamid = "outbound:88", text = "Editado local", isAdmin = true)

        assertEquals("Editado local", log.message)
        assertEquals("outbound:88", dto.id)
        assertNotNull(log.editedAt)
    }

    @Test
    fun `deleteOutboundMessage resolves by numeric local id`() {
        val created = LocalDateTime.now().minusHours(1)
        val log = WhatsAppMessageLog(
            id = 89,
            phone = "51902354183",
            metaMessageId = null,
            messageType = "OPERATOR_REPLY",
            status = "SENT",
            message = "Borrar local",
            createdAt = created,
        )
        every { messageLogRepository.findByMetaMessageId("89") } returns null
        every { messageLogRepository.findById(89) } returns java.util.Optional.of(log)
        every { messageLogRepository.save(any()) } answers { firstArg() }

        val dto = service.deleteOutboundMessage(wamid = "89", isAdmin = true)

        assertNotNull(log.deletedAt)
        assertEquals("DELETED", dto.deliveryStatus)
    }

    @Test
    fun `editOutboundMessage throws 400-style message when identifier unknown`() {
        every { messageLogRepository.findByMetaMessageId("wamid.MISSING") } returns null
        every { inboundMessageRepository.findByMetaMessageId("wamid.MISSING") } returns null

        val ex = assertThrows(IllegalArgumentException::class.java) {
            service.editOutboundMessage(wamid = "wamid.MISSING", text = "X", isAdmin = true)
        }
        assertEquals(WhatsAppMessageMutationPolicy.NO_LOCAL_OUTBOUND_RECORD, ex.message)
    }

    @Test
    fun `editOutboundMessage rejects when only inbound exists for numeric id`() {
        every { messageLogRepository.findByMetaMessageId("123") } returns null
        every { messageLogRepository.findById(123) } returns java.util.Optional.empty()
        every { inboundMessageRepository.findById(123) } returns java.util.Optional.of(
            WhatsAppInboundMessage(
                id = 123,
                metaMessageId = "wamid.IN.123",
                phone = "51902354183",
                messageText = "Hola",
                messageType = "text",
            )
        )

        val ex = assertThrows(IllegalArgumentException::class.java) {
            service.editOutboundMessage(wamid = "123", text = "X", isAdmin = true)
        }
        assertEquals(WhatsAppMessageMutationPolicy.NO_LOCAL_OUTBOUND_RECORD, ex.message)
        verify { inboundMessageRepository.findById(123) }
    }

    @Test
    fun `buildHumanHandoffClientMessage within hours promises immediate advisor`() {
        val fridayAfternoon = LocalDateTime.of(2026, 7, 31, 17, 0)
        val message = service.buildHumanHandoffClientMessage(fridayAfternoon)
        assertTrue(message.contains("le atiende una persona"))
        assertTrue(message.contains("Su solicitud quedó registrada"))
        assertFalse(message.contains("caso quedó registrado"))
        assertTrue(message.contains("Si necesita algo más, escriba MENU."))
        assertFalse(message.contains("primera hora"))
    }

    @Test
    fun `buildHumanHandoffClientMessage after hours promises first business hour`() {
        val fridayEvening = LocalDateTime.of(2026, 7, 31, 17, 30)
        val message = service.buildHumanHandoffClientMessage(fridayEvening)
        assertTrue(message.contains("primera hora"))
        assertTrue(message.contains(whatsAppProperties.autoReply.secretaryHours))
        assertTrue(message.contains("Su solicitud quedó registrada"))
        assertFalse(message.contains("caso quedó registrado"))
        assertFalse(message.contains("le atiende una persona de nuestro equipo por este mismo chat"))
        assertTrue(message.contains("Si necesita algo más, escriba MENU."))
        assertEquals(1, Regex("primera hora").findAll(message).count())
        assertEquals(1, Regex(Regex.escape(whatsAppProperties.autoReply.secretaryHours)).findAll(message).count())
    }

    @Test
    fun `support case handoff keeps caso wording`() {
        val fridayEvening = LocalDateTime.of(2026, 7, 31, 17, 30)
        val message = service.buildHumanHandoffClientMessage(
            now = fridayEvening,
            kind = WhatsAppHandoffCopyKind.SUPPORT_CASE
        )
        assertTrue(message.contains("Su caso quedó registrado"))
        assertFalse(message.contains("solicitud quedó registrada"))
    }

    @Test
    fun `installation handoff uses solicitud de instalacion`() {
        val fridayAfternoon = LocalDateTime.of(2026, 7, 31, 17, 0)
        val message = service.buildHumanHandoffClientMessage(
            now = fridayAfternoon,
            kind = WhatsAppHandoffCopyKind.INSTALLATION
        )
        assertTrue(message.contains("solicitud de instalación quedó registrada"))
        assertFalse(message.contains("caso quedó registrado"))
    }

    @Test
    fun `buildHumanHandoffClientMessage saturday afternoon is after hours`() {
        val saturdayAfternoon = LocalDateTime.of(2026, 7, 25, 13, 0)
        val message = service.buildHumanHandoffClientMessage(saturdayAfternoon)
        assertTrue(message.contains("primera hora"))
    }

    @Test
    fun `buildAfterHoursHandoffMessage is concise without repeating schedule`() {
        val message = service.buildAfterHoursHandoffMessage()
        assertTrue(message.startsWith("✅ Su solicitud quedó registrada."))
        assertTrue(message.contains("primera hora"))
        assertTrue(message.contains(whatsAppProperties.autoReply.secretaryHours))
        assertTrue(message.contains("Nuestro horario es de"))
        assertTrue(message.contains("Si necesita algo más, escriba MENU."))
        assertFalse(message.contains("Horario estimado de atencion"))
        assertFalse(message.contains("Lun-Vie"))
        assertEquals(1, Regex("primera hora").findAll(message).count())
    }

    @Test
    fun `buildAckResponse thanks without asking for menu selection`() {
        val message = service.buildAckResponse(null)
        assertTrue(message.contains("Gracias"))
        assertTrue(message.contains("MENU") || message.contains("ASESOR"))
        assertFalse(message.contains("seleccione una opcion"))
        assertFalse(message.contains("selecciona"))
    }

    @Test
    fun `buildVoucherReceivedResponse after hours uses first business hour follow-up`() {
        val sunday = LocalDateTime.of(2026, 7, 26, 10, 0)
        val message = service.buildVoucherReceivedResponse(sunday)
        assertTrue(message.contains("comprobante"))
        assertTrue(message.contains("primera hora"))
        assertTrue(message.contains(whatsAppProperties.autoReply.secretaryHours))
        assertTrue(message.contains("Nuestro horario es de"))
        assertFalse(message.contains("a la brevedad"))
        assertEquals(1, Regex("primera hora").findAll(message).count())
        assertEquals(1, Regex(Regex.escape(whatsAppProperties.autoReply.secretaryHours)).findAll(message).count())
    }

    @Test
    fun `buildReceiptPendingAckResponse after hours uses first business hour follow-up`() {
        val sunday = LocalDateTime.of(2026, 7, 26, 10, 0)
        val message = service.buildReceiptPendingAckResponse(sunday)
        assertTrue(message.contains("comprobante"))
        assertTrue(message.contains("primera hora"))
        assertFalse(message.contains("en breve"))
    }

    @Test
    fun `buildAfterHoursAckMessage confirms without registering a case`() {
        val message = service.buildAfterHoursAckMessage()
        assertTrue(message.contains("primera hora"))
        assertTrue(message.contains("Nuestro horario es de"))
        assertTrue(message.contains(whatsAppProperties.autoReply.secretaryHours))
        assertTrue(message.startsWith("Recibido, gracias."))
        assertFalse(message.contains("Tu caso fue registrado"))
        assertFalse(message.contains("caso fue registrado"))
        assertFalse(message.contains("caso quedo registrado"))
        assertFalse(message.contains("caso quedó registrado"))
        assertTrue(message.contains("Si necesita algo más, escriba MENU."))
    }

    @Test
    fun `buildDebtResponse with unpaid balance does not thank for being up to date`() {
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

        val response = service.buildDebtResponse(subscription)
        assertTrue(response.contains("50.00"))
        assertFalse(response.contains("mantenerte al día", ignoreCase = true))
        assertFalse(response.contains("mantenerte al dia", ignoreCase = true))
        assertTrue(response.contains("regularizar") || response.contains("realizar su pago") || response.contains("BCP"))
    }

    @Test
    fun `buildPaymentProofRequest uses usted`() {
        val message = service.buildPaymentProofRequest(null)
        assertTrue(message.contains("su pago"))
        assertTrue(message.contains("el comprobante") || message.contains("su comprobante"))
        assertTrue(message.contains("Cuando lo recibamos"))
        assertFalse(message.contains("tu comprobante"))
        assertFalse(message.contains("Envíanos tu"))
    }

    @Test
    fun `buildVoucherReceivedResponse within hours uses usted`() {
        val fridayMorning = LocalDateTime.of(2026, 7, 31, 9, 0)
        val message = service.buildVoucherReceivedResponse(fridayMorning)
        assertTrue(message.contains("su comprobante"))
        assertTrue(message.contains("le confirmaremos") || message.contains("en breve"))
    }

    @Test
    fun `main menu greeting uses usted`() {
        val bodySlot = slot<String>()
        every {
            whatsAppService.sendInteractiveListMessage(
                phoneNumber = any(),
                bodyText = capture(bodySlot),
                buttonText = any(),
                sectionTitle = any(),
                rows = any(),
                footerText = any(),
                contextMessageId = any()
            )
        } returns WhatsAppSendResult(
            success = true,
            metaResponse = "{}",
            metaMessageId = "wamid.menu",
            recipient = "51902354183",
            senderPhoneNumberId = "123"
        )

        service.sendMainMenu(
            phone = "51902354183",
            subscription = null,
            includeGreeting = true
        )

        assertTrue(bodySlot.captured.contains("Le atiende el asistente virtual"))
        assertTrue(bodySlot.captured.contains("podemos ayudarle"))
        assertFalse(bodySlot.captured.contains("Te atiende"))
        assertFalse(bodySlot.captured.contains("te podemos ayudar"))
    }

    @Test
    fun `buildPaymentProofReminder uses natural usted copy`() {
        val message = service.buildPaymentProofReminder()
        assertTrue(message.contains("Aún no hemos recibido su comprobante"))
        assertTrue(message.contains("Menú principal"))
        assertFalse(message.contains("Seguimos esperando"))
    }

    @Test
    fun `buildDebtResponse drops estimado and humanizes plan label`() {
        val subscription = Subscription(
            firstName = "Saul",
            lastName = "Leon",
            phone = "902354183",
            serviceStatus = ServiceStatus.ACTIVE,
            equipmentCondition = EquipmentCondition.LOAN
        ).apply {
            id = 77
            plan = com.dscorp.wispadmin.wispadmin.data.model.Plan(id = 1, name = "basico_wireless 50")
        }
        every {
            paymentRepository.findUnpaidBySubscriptionIdOrderByBillingDateDatetimeAsc(77)
        } returns listOf(
            Payment(
                discountAmount = 0.0,
                paid = false,
                amountToPay = 100.0,
                billingDateDatetime = LocalDateTime.of(2026, 4, 30, 0, 0)
            )
        )

        val response = service.buildDebtResponse(subscription)
        assertTrue(response.contains("Saul Leon, su saldo pendiente al día de hoy"))
        assertTrue(response.contains("Basico Wireless 50"))
        assertFalse(response.contains("Estimado"))
        assertFalse(response.contains("basico_wireless"))
    }

    @Test
    fun `buildPaidResponse pluralizes pending invoices cleanly`() {
        val subscription = Subscription(
            firstName = "Saul",
            lastName = "Leon",
            phone = "902354183",
            serviceStatus = ServiceStatus.ACTIVE,
            equipmentCondition = EquipmentCondition.LOAN
        ).apply { id = 78 }
        every {
            paymentRepository.findUnpaidBySubscriptionIdOrderByBillingDateDatetimeAsc(78)
        } returns listOf(
            Payment(
                discountAmount = 0.0,
                paid = false,
                amountToPay = 50.0,
                billingDateDatetime = LocalDateTime.of(2026, 4, 30, 0, 0)
            ),
            Payment(
                discountAmount = 0.0,
                paid = false,
                amountToPay = 50.0,
                billingDateDatetime = LocalDateTime.of(2026, 5, 31, 0, 0)
            )
        )

        val response = service.buildPaidResponse(subscription, LocalDateTime.of(2026, 7, 31, 10, 0))
        assertTrue(response.contains("Gracias por avisarnos, Saul."))
        assertTrue(response.contains("2 facturas pendientes"))
        assertFalse(response.contains("pendiente(s)"))
        assertFalse(response.contains("Hola Saul"))
    }
}
