package com.dscorp.wispadmin.wispadmin.controller

import com.dscorp.wispadmin.wispadmin.dto.CrmRealtimeEventDto
import com.dscorp.wispadmin.wispadmin.service.whatsapp.CrmEventPublisher
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
class WhatsAppEventsControllerTest {

    private val crmEventPublisher = mockk<CrmEventPublisher>()
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
        conversationService = mockk(relaxed = true),
        conversationQueryService = mockk(relaxed = true),
        mediaDownloadService = mockk(relaxed = true),
        handoffService = mockk(relaxed = true),
        csvExportService = mockk(relaxed = true),
        auditService = mockk(relaxed = true),
        crmEventPublisher = crmEventPublisher,
        batchSendJobService = mockk(relaxed = true)
    )
    private val mockMvc = MockMvcBuilders.standaloneSetup(controller).build()

    @Test
    fun `GET events returns catch-up list sinceEventId`() {
        every { crmEventPublisher.findSince(15L) } returns listOf(
            CrmRealtimeEventDto(
                eventId = 16L,
                eventType = CrmEventPublisher.MESSAGE_RECEIVED,
                payload = mapOf("phone" to "51902354183"),
                createdAt = "2026-08-02T09:00"
            )
        )

        mockMvc.perform(get("/whatsapp/events").param("sinceEventId", "15"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].eventId").value(16))
            .andExpect(jsonPath("$[0].eventType").value("MESSAGE_RECEIVED"))
            .andExpect(jsonPath("$[0].payload.phone").value("51902354183"))

        verify { crmEventPublisher.findSince(15L) }
    }

    @Test
    fun `GET events defaults sinceEventId to zero`() {
        every { crmEventPublisher.findSince(0L) } returns emptyList()

        val response = mockMvc.perform(get("/whatsapp/events"))
            .andExpect(status().isOk)
            .andReturn()

        assertEquals("[]", response.response.contentAsString)
        verify { crmEventPublisher.findSince(0L) }
    }
}
