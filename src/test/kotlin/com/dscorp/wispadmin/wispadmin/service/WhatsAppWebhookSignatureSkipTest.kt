package com.dscorp.wispadmin.wispadmin.service

import com.dscorp.wispadmin.wispadmin.config.WhatsAppProperties
import com.dscorp.wispadmin.wispadmin.repository.WhatsAppMessageLogRepository
import com.dscorp.wispadmin.wispadmin.repository.WhatsAppWebhookEventRepository
import com.dscorp.wispadmin.wispadmin.service.whatsapp.WhatsAppAccountEventService
import com.dscorp.wispadmin.wispadmin.service.whatsapp.WhatsAppServiceWindowService
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WhatsAppWebhookSignatureSkipTest {

    @Test
    fun `verifySignature returns true when signature not required`() {
        val properties = WhatsAppProperties().apply {
            webhookSignatureRequired = false
        }
        val service = WhatsAppWebhookService(
            whatsAppProperties = properties,
            whatsAppMessageLogRepository = mockk(relaxed = true),
            whatsAppWebhookEventRepository = mockk(relaxed = true),
            whatsAppInboundMessageService = mockk(relaxed = true),
            accountEventService = mockk(relaxed = true),
            serviceWindowService = mockk(relaxed = true),
            crmEventPublisher = mockk(relaxed = true)
        )

        assertTrue(service.verifySignature("{}".toByteArray(), null))
    }
}
