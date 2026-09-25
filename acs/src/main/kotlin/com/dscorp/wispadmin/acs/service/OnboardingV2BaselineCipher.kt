package com.dscorp.wispadmin.acs.service

import com.dscorp.wispadmin.acs.config.AcsProperties
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** ACS-local encryption: plaintext baseline never leaves the ACS process or its encrypted column. */
class OnboardingV2BaselineCipher(properties: AcsProperties) {
    private val key = properties.provisioningBaselineKey.trim().takeIf { it.isNotEmpty() }?.let {
        SecretKeySpec(MessageDigest.getInstance("SHA-256").digest(it.toByteArray()), "AES")
    }

    fun encrypt(value: String): String {
        val encryptionKey = requireNotNull(key) { "ACS_V2_BASELINE_KEY_REQUIRED" }
        val iv = ByteArray(12).also(SecureRandom()::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, encryptionKey, GCMParameterSpec(128, iv)) }
        return Base64.getEncoder().encodeToString(iv + cipher.doFinal(value.toByteArray()))
    }

    fun decrypt(value: String): String {
        val encryptionKey = requireNotNull(key) { "ACS_V2_BASELINE_KEY_REQUIRED" }
        val raw = Base64.getDecoder().decode(value)
        require(raw.size > 12) { "ACS_V2_BASELINE_CIPHER_INVALID" }
        return Cipher.getInstance("AES/GCM/NoPadding").run {
            init(Cipher.DECRYPT_MODE, encryptionKey, GCMParameterSpec(128, raw.copyOfRange(0, 12)))
            doFinal(raw.copyOfRange(12, raw.size)).toString(Charsets.UTF_8)
        }
    }
}
