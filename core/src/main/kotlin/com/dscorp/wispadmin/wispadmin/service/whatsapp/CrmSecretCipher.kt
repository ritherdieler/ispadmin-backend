package com.dscorp.wispadmin.wispadmin.service.whatsapp

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class CrmSecretCipher(masterKey: String) {
    private val keyBytes: ByteArray = deriveKey(masterKey)

    fun encrypt(plain: String): String {
        val iv = ByteArray(IV_LENGTH).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(keyBytes, "AES"), GCMParameterSpec(TAG_BITS, iv))
        val encrypted = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        val payload = ByteArray(iv.size + encrypted.size)
        System.arraycopy(iv, 0, payload, 0, iv.size)
        System.arraycopy(encrypted, 0, payload, iv.size, encrypted.size)
        return PREFIX + Base64.getEncoder().encodeToString(payload)
    }

    fun decrypt(encrypted: String): String {
        if (!looksEncrypted(encrypted)) {
            throw IllegalStateException("Payload cifrado invalido")
        }
        val raw = Base64.getDecoder().decode(encrypted.removePrefix(PREFIX))
        if (raw.size <= IV_LENGTH) {
            throw IllegalStateException("Payload cifrado incompleto")
        }
        val iv = raw.copyOfRange(0, IV_LENGTH)
        val cipherBytes = raw.copyOfRange(IV_LENGTH, raw.size)
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(keyBytes, "AES"), GCMParameterSpec(TAG_BITS, iv))
            String(cipher.doFinal(cipherBytes), Charsets.UTF_8)
        } catch (e: Exception) {
            throw IllegalStateException("No se pudo descifrar el secreto CRM", e)
        }
    }

    fun looksEncrypted(value: String?): Boolean =
        !value.isNullOrBlank() && value.startsWith(PREFIX)

    fun maskApiKey(apiKey: String?): String? {
        if (apiKey.isNullOrBlank()) return null
        if (apiKey.length < 8) return "****"
        return "sk-...${apiKey.takeLast(4)}"
    }

    private fun deriveKey(masterKey: String): ByteArray {
        val trimmed = masterKey.trim()
        if (trimmed.isBlank()) {
            throw IllegalArgumentException("CRM_SECRETS_MASTER_KEY / crm.llm.master-key es obligatorio para cifrar secretos")
        }
        return MessageDigest.getInstance("SHA-256").digest(trimmed.toByteArray(Charsets.UTF_8))
    }

    companion object {
        private const val PREFIX = "enc:v1:"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_LENGTH = 12
        private const val TAG_BITS = 128
    }
}
