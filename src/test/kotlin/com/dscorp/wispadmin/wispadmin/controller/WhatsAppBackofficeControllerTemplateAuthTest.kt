package com.dscorp.wispadmin.wispadmin.controller

import com.dscorp.wispadmin.wispadmin.dto.WhatsAppTestSendResponseDto
import com.dscorp.wispadmin.wispadmin.requestbody.WhatsAppConversationTemplateBody
import com.dscorp.wispadmin.wispadmin.requestbody.WhatsAppSelectedRemindersRequest
import com.dscorp.wispadmin.wispadmin.requestbody.WhatsAppSendMessagesRequest
import com.dscorp.wispadmin.wispadmin.requestbody.WhatsAppTemplateTestMessageRequest
import com.dscorp.wispadmin.wispadmin.security.PlatformAuthFilter
import com.dscorp.wispadmin.wispadmin.service.whatsapp.CrmEventPublisher
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.mock.web.MockHttpServletRequest

class WhatsAppBackofficeControllerTemplateAuthTest {

    private val messageService = mockk<com.dscorp.wispadmin.wispadmin.service.WhatsAppBackofficeMessageService>(relaxed = true)
    private val templateMessageSender = mockk<WhatsAppTemplateMessageSender>()
    private val conversationService = mockk<com.dscorp.wispadmin.wispadmin.service.whatsapp.WhatsAppConversationService>(relaxed = true)
    private lateinit var controller: WhatsAppBackofficeController

    @BeforeEach
    fun setUp() {
        controller = WhatsAppBackofficeController(
            messageService = messageService,
            queryService = mockk(relaxed = true),
            messageLogRepository = mockk(relaxed = true),
            inboundMessageRepository = mockk(relaxed = true),
            templateMessageSender = templateMessageSender,
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
            crmEventPublisher = mockk<CrmEventPublisher>(relaxed = true)
        )
    }

    @Test
    fun `sendSelectedMessages forbidden for SECRETARY`() {
        val response = controller.sendSelectedMessages(
            WhatsAppSendMessagesRequest(templateCode = "PAYMENT_REMINDER", targetIds = listOf(1)),
            secretaryRequest()
        )
        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        verify(exactly = 0) { messageService.sendSelected(any(), any(), any()) }
    }

    @Test
    fun `sendSelectedReminders forbidden for SECRETARY`() {
        val response = controller.sendSelectedReminders(
            WhatsAppSelectedRemindersRequest(paymentIds = listOf(1)),
            secretaryRequest()
        )
        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        verify(exactly = 0) { messageService.sendSelectedReminders(any(), any()) }
    }

    @Test
    fun `sendManualTemplateMessage forbidden for SECRETARY`() {
        val response = controller.sendManualTemplateMessage(
            WhatsAppTemplateTestMessageRequest(
                phoneNumber = "51999999999",
                clientName = "Test",
                amount = "50",
                billingPeriod = "Mayo"
            ),
            secretaryRequest()
        )
        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        verify(exactly = 0) { templateMessageSender.sendPaymentReminderTemplate(any()) }
    }

    @Test
    fun `sendConversationTemplate forbidden for SECRETARY`() {
        val response = controller.sendConversationTemplate(
            "51999999999",
            WhatsAppConversationTemplateBody(templateCode = "PAYMENT_REMINDER"),
            secretaryRequest()
        )
        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        verify(exactly = 0) {
            conversationService.sendOperatorTemplate(any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `sendManualTemplateMessage allowed for ADMIN`() {
        every { templateMessageSender.sendPaymentReminderTemplate(any()) } returns WhatsAppTestSendResponseDto(
            success = true,
            message = "ok",
            recipient = "51999999999",
            senderPhoneNumberId = "1234",
            metaResponse = "{}",
            deliveryHint = ""
        )
        val response = controller.sendManualTemplateMessage(
            WhatsAppTemplateTestMessageRequest(
                phoneNumber = "51999999999",
                clientName = "Test",
                amount = "50",
                billingPeriod = "Mayo"
            ),
            adminRequest()
        )
        assertEquals(HttpStatus.OK, response.statusCode)
        verify(exactly = 1) { templateMessageSender.sendPaymentReminderTemplate(any()) }
    }

    private fun adminRequest() = MockHttpServletRequest().apply {
        setAttribute(PlatformAuthFilter.AUTH_USER_ID_ATTRIBUTE, 1)
        setAttribute(PlatformAuthFilter.AUTH_USERNAME_ATTRIBUTE, "admin")
        setAttribute(PlatformAuthFilter.AUTH_USER_TYPE_ATTRIBUTE, "ADMIN")
    }

    private fun secretaryRequest() = MockHttpServletRequest().apply {
        setAttribute(PlatformAuthFilter.AUTH_USER_ID_ATTRIBUTE, 2)
        setAttribute(PlatformAuthFilter.AUTH_USERNAME_ATTRIBUTE, "sec")
        setAttribute(PlatformAuthFilter.AUTH_USER_TYPE_ATTRIBUTE, "SECRETARY")
    }
}
