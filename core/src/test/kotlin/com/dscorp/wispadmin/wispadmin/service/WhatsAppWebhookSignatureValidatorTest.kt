package com.dscorp.wispadmin.wispadmin.service

import com.dscorp.wispadmin.wispadmin.config.WhatsAppProperties
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class WhatsAppWebhookSignatureValidatorTest {

    @Test
    fun `skips validation when app secret is blank`() {
        val properties = WhatsAppProperties().apply { appSecret = "" }
        val validator = WhatsAppWebhookSignatureValidator(properties)

        assertTrue(validator.isValid("{}", null))
    }

    @Test
    fun `accepts valid signature when app secret is configured`() {
        val secret = "test_app_secret"
        val payload = """{"object":"whatsapp_business_account"}"""
        val signature = "sha256=" + hmacSha256Hex(secret, payload)

        val properties = WhatsAppProperties().apply { appSecret = secret }
        val validator = WhatsAppWebhookSignatureValidator(properties)

        assertTrue(validator.isValid(payload, signature))
    }

    @Test
    fun `rejects invalid signature when app secret is configured`() {
        val properties = WhatsAppProperties().apply { appSecret = "test_app_secret" }
        val validator = WhatsAppWebhookSignatureValidator(properties)

        assertFalse(validator.isValid("{}", "sha256=invalid"))
    }

    private fun hmacSha256Hex(secret: String, payload: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return mac.doFinal(payload.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }
    }
}
