package com.dscorp.wispadmin.wispadmin.controller

import com.dscorp.wispadmin.wispadmin.dto.WhatsAppThreadMessageDto
import com.dscorp.wispadmin.wispadmin.security.PlatformAuthFilter
import com.dscorp.wispadmin.wispadmin.service.whatsapp.CrmEventPublisher
import com.dscorp.wispadmin.wispadmin.service.whatsapp.WhatsAppConversationService
import com.dscorp.wispadmin.wispadmin.service.whatsapp.WhatsAppMessageMutationPolicy
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.time.LocalDateTime

class WhatsAppMessageMutationControllerTest {

    private val conversationService = mockk<WhatsAppConversationService>()
    private val crmEventPublisher = mockk<CrmEventPublisher>(relaxed = true)
    private val controller = WhatsAppBackofficeController(
        messageService = mockk(relaxed = true),
        queryService = mockk(relaxed = true),
        messageLogRepository = mockk(relaxed = true),
        inboundMessageRepository = mockk(relaxed = true),
        templateMessageSender = mockk(relaxed = true),
        welcomeRegistrationService = mockk(relaxed = true),
        whatsAppProperties = mockk(relaxed = true),
        analyticsService = mockk(relaxed = true),
        metaAnalyticsClient = mockk(relaxed = true),
        templateSyncService = mockk(relaxed = true),
        syncedTemplateRepository = mockk(relaxed = true),
        accountEventService = mockk(relaxed = true),
        serviceWindowService = mockk(relaxed = true),
        conversationService = conversationService,
        conversationQueryService = mockk(relaxed = true),
        mediaDownloadService = mockk(relaxed = true),
        handoffService = mockk(relaxed = true),
        csvExportService = mockk(relaxed = true),
        auditService = mockk(relaxed = true),
        crmEventPublisher = crmEventPublisher,
        batchSendJobService = mockk(relaxed = true),
    )
    private val mockMvc = MockMvcBuilders.standaloneSetup(controller).build()

    @Test
    fun `POST messages wamid react returns thread DTO`() {
        every {
            conversationService.reactToInboundMessage("wamid.IN.1", "❤️", agentId = 7, isAdmin = true)
        } returns WhatsAppThreadMessageDto(
            id = "inbound:1",
            direction = "INBOUND",
            body = "Hola",
            messageType = "text",
            buttonReplyTitle = null,
            hasMedia = false,
            mediaId = 1,
            deliveryStatus = null,
            createdAt = LocalDateTime.of(2026, 8, 6, 12, 0),
            replyToLogId = null,
            operatorUsername = null,
            reactionEmoji = "❤️",
            metaMessageId = "wamid.IN.1",
        )

        mockMvc.perform(
            post("/whatsapp/messages/{wamid}/react", "wamid.IN.1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"emoji":"❤️"}""")
                .requestAttr(PlatformAuthFilter.AUTH_USER_ID_ATTRIBUTE, 7)
                .requestAttr(PlatformAuthFilter.AUTH_USER_TYPE_ATTRIBUTE, "ADMIN"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value("inbound:1"))
            .andExpect(jsonPath("$.reactionEmoji").value("❤️"))

        verify {
            conversationService.reactToInboundMessage("wamid.IN.1", "❤️", agentId = 7, isAdmin = true)
        }
    }

    @Test
    fun `PUT messages wamid edits outbound`() {
        every {
            conversationService.editOutboundMessage("wamid.OUT.2", "Nuevo", agentId = 7, isAdmin = true)
        } returns WhatsAppThreadMessageDto(
            id = "outbound:2",
            direction = "OUTBOUND",
            body = "Nuevo",
            messageType = "OPERATOR_REPLY",
            buttonReplyTitle = null,
            hasMedia = false,
            mediaId = null,
            deliveryStatus = "SENT",
            createdAt = LocalDateTime.of(2026, 8, 6, 12, 0),
            replyToLogId = null,
            operatorUsername = "agent",
            editedAt = LocalDateTime.of(2026, 8, 6, 12, 5),
            metaMessageId = "wamid.OUT.2",
        )

        mockMvc.perform(
            put("/whatsapp/messages/{wamid}", "wamid.OUT.2")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"text":"Nuevo"}""")
                .requestAttr(PlatformAuthFilter.AUTH_USER_ID_ATTRIBUTE, 7)
                .requestAttr(PlatformAuthFilter.AUTH_USER_TYPE_ATTRIBUTE, "ADMIN"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.body").value("Nuevo"))
            .andExpect(jsonPath("$.editedAt").exists())
    }

    @Test
    fun `DELETE messages wamid soft deletes outbound`() {
        every {
            conversationService.deleteOutboundMessage("wamid.OUT.3", agentId = 7, isAdmin = true)
        } returns WhatsAppThreadMessageDto(
            id = "outbound:3",
            direction = "OUTBOUND",
            body = null,
            messageType = "OPERATOR_REPLY",
            buttonReplyTitle = null,
            hasMedia = false,
            mediaId = null,
            deliveryStatus = "DELETED",
            createdAt = LocalDateTime.of(2026, 8, 6, 12, 0),
            replyToLogId = null,
            operatorUsername = "agent",
            deletedAt = LocalDateTime.of(2026, 8, 6, 12, 30),
            metaMessageId = "wamid.OUT.3",
        )

        mockMvc.perform(
            delete("/whatsapp/messages/{wamid}", "wamid.OUT.3")
                .requestAttr(PlatformAuthFilter.AUTH_USER_ID_ATTRIBUTE, 7)
                .requestAttr(PlatformAuthFilter.AUTH_USER_TYPE_ATTRIBUTE, "ADMIN"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.deliveryStatus").value("DELETED"))
            .andExpect(jsonPath("$.deletedAt").exists())
    }

    @Test
    fun `PUT messages wamid returns 400 when edit window expired`() {
        every {
            conversationService.editOutboundMessage("wamid.OUT.expired", "Nuevo", agentId = 7, isAdmin = true)
        } throws IllegalArgumentException(WhatsAppMessageMutationPolicy.EDIT_EXPIRED_MESSAGE)

        mockMvc.perform(
            put("/whatsapp/messages/{wamid}", "wamid.OUT.expired")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"text":"Nuevo"}""")
                .requestAttr(PlatformAuthFilter.AUTH_USER_ID_ATTRIBUTE, 7)
                .requestAttr(PlatformAuthFilter.AUTH_USER_TYPE_ATTRIBUTE, "ADMIN"),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value(WhatsAppMessageMutationPolicy.EDIT_EXPIRED_MESSAGE))
    }

    @Test
    fun `PUT messages wamid accepts body alias`() {
        every {
            conversationService.editOutboundMessage("wamid.OUT.body", "Desde body", agentId = 7, isAdmin = true)
        } returns WhatsAppThreadMessageDto(
            id = "outbound:9",
            direction = "OUTBOUND",
            body = "Desde body",
            messageType = "OPERATOR_REPLY",
            buttonReplyTitle = null,
            hasMedia = false,
            mediaId = null,
            deliveryStatus = "SENT",
            createdAt = LocalDateTime.of(2026, 8, 6, 12, 0),
            replyToLogId = null,
            operatorUsername = "agent",
            editedAt = LocalDateTime.of(2026, 8, 6, 12, 5),
            metaMessageId = "wamid.OUT.body",
        )

        mockMvc.perform(
            put("/whatsapp/messages/{wamid}", "wamid.OUT.body")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"body":"Desde body"}""")
                .requestAttr(PlatformAuthFilter.AUTH_USER_ID_ATTRIBUTE, 7)
                .requestAttr(PlatformAuthFilter.AUTH_USER_TYPE_ATTRIBUTE, "ADMIN"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.body").value("Desde body"))
    }

    @Test
    fun `PUT and DELETE return 200 for wamid and numeric local id`() {
        every {
            conversationService.editOutboundMessage("wamid.HBg.OUT.META", "Editado", agentId = 7, isAdmin = true)
        } returns WhatsAppThreadMessageDto(
            id = "outbound:90",
            direction = "OUTBOUND",
            body = "Editado",
            messageType = "OPERATOR_REPLY",
            buttonReplyTitle = null,
            hasMedia = false,
            mediaId = null,
            deliveryStatus = "SENT",
            createdAt = LocalDateTime.of(2026, 8, 6, 12, 0),
            replyToLogId = null,
            operatorUsername = "agent",
            editedAt = LocalDateTime.of(2026, 8, 6, 12, 5),
            metaMessageId = "wamid.HBg.OUT.META",
        )
        every {
            conversationService.editOutboundMessage("90", "Editado num", agentId = 7, isAdmin = true)
        } returns WhatsAppThreadMessageDto(
            id = "outbound:90",
            direction = "OUTBOUND",
            body = "Editado num",
            messageType = "OPERATOR_REPLY",
            buttonReplyTitle = null,
            hasMedia = false,
            mediaId = null,
            deliveryStatus = "SENT",
            createdAt = LocalDateTime.of(2026, 8, 6, 12, 0),
            replyToLogId = null,
            operatorUsername = "agent",
            editedAt = LocalDateTime.of(2026, 8, 6, 12, 5),
            metaMessageId = null,
        )
        every {
            conversationService.deleteOutboundMessage("wamid.HBg.OUT.META", agentId = 7, isAdmin = true)
        } returns WhatsAppThreadMessageDto(
            id = "outbound:90",
            direction = "OUTBOUND",
            body = null,
            messageType = "OPERATOR_REPLY",
            buttonReplyTitle = null,
            hasMedia = false,
            mediaId = null,
            deliveryStatus = "DELETED",
            createdAt = LocalDateTime.of(2026, 8, 6, 12, 0),
            replyToLogId = null,
            operatorUsername = "agent",
            deletedAt = LocalDateTime.of(2026, 8, 6, 12, 30),
            metaMessageId = "wamid.HBg.OUT.META",
        )
        every {
            conversationService.deleteOutboundMessage("91", agentId = 7, isAdmin = true)
        } returns WhatsAppThreadMessageDto(
            id = "outbound:91",
            direction = "OUTBOUND",
            body = null,
            messageType = "OPERATOR_REPLY",
            buttonReplyTitle = null,
            hasMedia = false,
            mediaId = null,
            deliveryStatus = "DELETED",
            createdAt = LocalDateTime.of(2026, 8, 6, 12, 0),
            replyToLogId = null,
            operatorUsername = "agent",
            deletedAt = LocalDateTime.of(2026, 8, 6, 12, 30),
            metaMessageId = null,
        )

        // Raw dotted Meta path (not URI template) — reproduces real Axios URLs with dots in wamid
        mockMvc.perform(
            put("/whatsapp/messages/wamid.HBg.OUT.META")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"text":"Editado"}""")
                .requestAttr(PlatformAuthFilter.AUTH_USER_ID_ATTRIBUTE, 7)
                .requestAttr(PlatformAuthFilter.AUTH_USER_TYPE_ATTRIBUTE, "ADMIN"),
        ).andExpect(status().isOk)

        mockMvc.perform(
            put("/whatsapp/messages/90")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"text":"Editado num"}""")
                .requestAttr(PlatformAuthFilter.AUTH_USER_ID_ATTRIBUTE, 7)
                .requestAttr(PlatformAuthFilter.AUTH_USER_TYPE_ATTRIBUTE, "ADMIN"),
        ).andExpect(status().isOk)

        mockMvc.perform(
            delete("/whatsapp/messages/wamid.HBg.OUT.META")
                .requestAttr(PlatformAuthFilter.AUTH_USER_ID_ATTRIBUTE, 7)
                .requestAttr(PlatformAuthFilter.AUTH_USER_TYPE_ATTRIBUTE, "ADMIN"),
        ).andExpect(status().isOk)

        mockMvc.perform(
            delete("/whatsapp/messages/91")
                .requestAttr(PlatformAuthFilter.AUTH_USER_ID_ATTRIBUTE, 7)
                .requestAttr(PlatformAuthFilter.AUTH_USER_TYPE_ATTRIBUTE, "ADMIN"),
        ).andExpect(status().isOk)

        verify {
            conversationService.editOutboundMessage("wamid.HBg.OUT.META", "Editado", agentId = 7, isAdmin = true)
            conversationService.editOutboundMessage("90", "Editado num", agentId = 7, isAdmin = true)
            conversationService.deleteOutboundMessage("wamid.HBg.OUT.META", agentId = 7, isAdmin = true)
            conversationService.deleteOutboundMessage("91", agentId = 7, isAdmin = true)
        }
    }

    @Test
    fun `PUT messages returns 400 when message identifier unknown`() {
        every {
            conversationService.editOutboundMessage("wamid.MISSING", "X", agentId = 7, isAdmin = true)
        } throws IllegalArgumentException(WhatsAppMessageMutationPolicy.NO_LOCAL_OUTBOUND_RECORD)

        mockMvc.perform(
            put("/whatsapp/messages/wamid.MISSING")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"text":"X"}""")
                .requestAttr(PlatformAuthFilter.AUTH_USER_ID_ATTRIBUTE, 7)
                .requestAttr(PlatformAuthFilter.AUTH_USER_TYPE_ATTRIBUTE, "ADMIN"),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value(WhatsAppMessageMutationPolicy.NO_LOCAL_OUTBOUND_RECORD))
    }
}
