package com.dscorp.wispadmin.wispadmin.service

import com.dscorp.wispadmin.wispadmin.config.WhatsAppProperties
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@Component
class WhatsAppWebhookSignatureValidator(
    private val whatsAppProperties: WhatsAppProperties
) {

    private val log = LoggerFactory.getLogger(this::class.java)

    fun isValid(rawBody: String, signatureHeader: String?): Boolean {
        val appSecret = whatsAppProperties.appSecret
        if (appSecret.isBlank()) {
            return true
        }

        if (signatureHeader.isNullOrBlank()) {
            log.warn("WhatsApp webhook rejected: missing X-Hub-Signature-256 header")
            return false
        }

        val expectedPrefix = "sha256="
        if (!signatureHeader.startsWith(expectedPrefix, ignoreCase = true)) {
            log.warn("WhatsApp webhook rejected: invalid signature format")
            return false
        }

        val providedSignature = signatureHeader.substring(expectedPrefix.length)
        val expectedSignature = hmacSha256Hex(appSecret, rawBody)

        return MessageDigest.isEqual(
            expectedSignature.lowercase().toByteArray(),
            providedSignature.lowercase().toByteArray()
        )
    }

    private fun hmacSha256Hex(secret: String, payload: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return mac.doFinal(payload.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }
    }
}
