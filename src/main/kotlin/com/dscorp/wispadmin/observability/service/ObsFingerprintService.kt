package com.dscorp.wispadmin.observability.service

import org.springframework.stereotype.Service
import java.security.MessageDigest

@Service
class ObsFingerprintService {

    private val digitsRegex = Regex("\\d+")
    private val hexRegex = Regex("0x[0-9a-fA-F]+")
    private val whitespaceRegex = Regex("\\s+")

    fun fingerprint(
        platform: String?,
        errorType: String?,
        stacktrace: String?,
        message: String?
    ): String {
        val basis = normalizedStack(stacktrace).ifBlank { normalizeMessage(message) }
        val raw = listOf(
            platform?.trim()?.lowercase() ?: "",
            errorType?.trim() ?: "",
            basis
        ).joinToString("|")
        return sha256(raw)
    }

    private fun normalizedStack(stacktrace: String?): String {
        if (stacktrace.isNullOrBlank()) return ""
        val frames = stacktrace.lines()
            .map { it.trim() }
            .filter { it.startsWith("at ") || it.contains("(") }
            .take(8)
            .joinToString("\n")
        val basis = if (frames.isNotBlank()) frames else stacktrace.lines().take(3).joinToString("\n")
        return normalize(basis)
    }

    private fun normalizeMessage(message: String?): String {
        if (message.isNullOrBlank()) return ""
        return normalize(message.take(300))
    }

    private fun normalize(value: String): String {
        return value
            .replace(hexRegex, "0xADDR")
            .replace(digitsRegex, "N")
            .replace(whitespaceRegex, " ")
            .trim()
            .lowercase()
    }

    private fun sha256(value: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest(value.toByteArray(Charsets.UTF_8))
        val sb = StringBuilder()
        for (b in bytes) {
            val hex = Integer.toHexString(0xff and b.toInt())
            if (hex.length == 1) sb.append('0')
            sb.append(hex)
        }
        return sb.toString()
    }
}
