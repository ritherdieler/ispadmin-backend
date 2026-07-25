package com.dscorp.wispadmin.wispadmin.service

import com.dscorp.wispadmin.wispadmin.data.model.WhatsAppInboundMessage
import com.dscorp.wispadmin.wispadmin.data.model.WhatsAppMessageLog
import com.dscorp.wispadmin.wispadmin.repository.WhatsAppInboundMessageRepository
import com.dscorp.wispadmin.wispadmin.repository.WhatsAppMessageLogRepository
import com.dscorp.wispadmin.wispadmin.service.whatsapp.WhatsAppConversationService
import com.dscorp.wispadmin.wispadmin.service.whatsapp.WhatsAppInboundPayload
import com.dscorp.wispadmin.wispadmin.service.whatsapp.WhatsAppMediaDownloadService
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class WhatsAppInboundMessageServiceTest {

    private val inboundMessageRepository = mockk<WhatsAppInboundMessageRepository>()
    private val conversationService = mockk<WhatsAppConversationService>()
    private val mediaDownloadService = mockk<WhatsAppMediaDownloadService>()
    private val whatsAppService = mockk<WhatsAppService>()
    private val messageLogRepository = mockk<WhatsAppMessageLogRepository>()

    private lateinit var service: WhatsAppInboundMessageService

    @BeforeEach
    fun setUp() {
        service = WhatsAppInboundMessageService(
            inboundMessageRepository = inboundMessageRepository,
            conversationService = conversationService,
            mediaDownloadService = mediaDownloadService,
            whatsAppService = whatsAppService,
            messageLogRepository = messageLogRepository
        )
    }

    @Test
    fun `processInboundMessage persists AUTO_REPLY log after successful text button reply`() {
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
        every {
            conversationService.handleButtonReply(payload.phone, "soporte", null)
        } returns "Para atencion personalizada."
        every {
            whatsAppService.sendTextMessage(payload.phone, "Para atencion personalizada.")
        } returns WhatsAppSendResult(
            success = true,
            metaResponse = "{}",
            metaMessageId = "wamid.auto-9",
            recipient = payload.phone,
            senderPhoneNumberId = "123"
        )
        val logSlot = slot<WhatsAppMessageLog>()
        every { messageLogRepository.save(capture(logSlot)) } answers { firstArg() }

        service.processInboundMessage(payload)

        assertEquals("AUTO_REPLY", logSlot.captured.messageType)
        assertEquals("SENT", logSlot.captured.status)
        assertEquals("Para atencion personalizada.", logSlot.captured.message)
        assertEquals("wamid.auto-9", logSlot.captured.metaMessageId)
        assertEquals(payload.phone, logSlot.captured.phone)
    }

    @Test
    fun `processInboundMessage persists AUTO_REPLY log after interactive buttons`() {
        val payload = WhatsAppInboundPayload(
            metaMessageId = "wamid.in-2",
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
            if (msg.id == null) msg.copy(id = 2) else msg
        }
        every { conversationService.findSubscriptionByPhone(payload.phone) } returns null
        every {
            conversationService.sendAutoReplyWithButtons(payload.phone, null)
        } returns WhatsAppConversationService.AutoReplyResult(
            success = true,
            messageText = "Hola, soy el asistente",
            metaMessageId = "wamid.auto-btn"
        )
        val logSlot = slot<WhatsAppMessageLog>()
        every { messageLogRepository.save(capture(logSlot)) } answers { firstArg() }

        service.processInboundMessage(payload)

        verify(exactly = 1) { messageLogRepository.save(any()) }
        assertEquals("AUTO_REPLY", logSlot.captured.messageType)
        assertEquals("wamid.auto-btn", logSlot.captured.metaMessageId)
        assertEquals("Hola, soy el asistente", logSlot.captured.message)
    }
}
