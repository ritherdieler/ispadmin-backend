package com.dscorp.wispadmin.wispadmin.util

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object PasswordHashUtil {
    private const val PREFIX = "pbkdf2"
    private const val ITERATIONS = 120000
    private const val KEY_LENGTH = 256
    private const val SALT_LENGTH = 16
    private val secureRandom = SecureRandom()

    fun hash(rawPassword: String): String {
        val salt = ByteArray(SALT_LENGTH)
        secureRandom.nextBytes(salt)
        val encodedSalt = Base64.getEncoder().encodeToString(salt)
        val encodedHash = Base64.getEncoder().encodeToString(pbkdf2(rawPassword, salt, ITERATIONS))
        return listOf(PREFIX, ITERATIONS.toString(), encodedSalt, encodedHash).joinToString("$")
    }

    fun matches(rawPassword: String, storedPassword: String?): Boolean {
        if (storedPassword.isNullOrBlank()) return false
        return matchedCredential(rawPassword, storedPassword) != null
    }

    fun passwordToStoreAfterLegacyLogin(rawPassword: String, storedPassword: String?): String {
        val matched = matchedCredential(rawPassword, storedPassword) ?: rawPassword
        return hash(matched)
    }

    private fun matchedCredential(rawPassword: String, storedPassword: String?): String? {
        if (storedPassword.isNullOrBlank()) return null

        val candidates = listOf(rawPassword, sha384(rawPassword)).distinct()
        if (!isHashed(storedPassword)) {
            return candidates.firstOrNull { it == storedPassword }
        }

        val parts = storedPassword.split("$")
        if (parts.size != 4 || parts[0] != PREFIX) return null

        return try {
            val iterations = parts[1].toInt()
            val salt = Base64.getDecoder().decode(parts[2])
            val expected = Base64.getDecoder().decode(parts[3])
            candidates.firstOrNull { credential ->
                val actual = pbkdf2(credential, salt, iterations)
                MessageDigest.isEqual(expected, actual)
            }
        } catch (e: Exception) {
            null
        }
    }

    fun isHashed(password: String?): Boolean {
        return password?.startsWith("$PREFIX$") == true
    }

    private fun pbkdf2(rawPassword: String, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(rawPassword.toCharArray(), salt, iterations, KEY_LENGTH)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }

    private fun sha384(rawPassword: String): String {
        val bytes = MessageDigest.getInstance("SHA-384").digest(rawPassword.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
