package com.dscorp.wispadmin.wispadmin.controller

import com.dscorp.wispadmin.wispadmin.config.WhatsAppProperties
import com.dscorp.wispadmin.wispadmin.service.WhatsAppWebhookService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.doNothing
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.mock

class WhatsAppWebhookSecurityTest {

    private val whatsAppProperties = WhatsAppProperties().apply {
        webhookVerifyToken = "test_verify_token"
    }
    private val whatsAppWebhookService = mock(WhatsAppWebhookService::class.java)
    private val controller = WhatsAppWebhookController(whatsAppProperties, whatsAppWebhookService)

    @Test
    fun `POST webhook returns 200 when signature valid`() {
        doReturn(true).`when`(whatsAppWebhookService).verifySignature(
            anyByteArray(),
            nullableString()
        )
        doNothing().`when`(whatsAppWebhookService).processPayloadAsync(ArgumentMatchers.anyString())

        val response = controller.receiveEvents(
            rawBody = """{"object":"whatsapp_business_account","entry":[]}""",
            signature = "sha256=abc"
        )

        assertEquals(200, response.statusCodeValue)
        assertEquals("EVENT_RECEIVED", response.body)
    }

    @Test
    fun `GET webhook verification returns challenge when token matches`() {
        val response = controller.verifyWebhook(
            mode = "subscribe",
            verifyToken = "test_verify_token",
            challenge = "12345"
        )

        assertEquals(200, response.statusCodeValue)
        assertEquals("12345", response.body)
    }

    private fun anyByteArray(): ByteArray {
        ArgumentMatchers.any(ByteArray::class.java)
        return ByteArray(0)
    }

    private fun nullableString(): String? {
        ArgumentMatchers.any(String::class.java)
        return null
    }
}
