package com.dscorp.wispadmin.wispadmin.oltclient

object OnuSerialNormalizer {
    private val paren = Regex("""\(([^)]+)\)""")
    private val hex16 = Regex("^[0-9A-F]{16}$")

    fun preferredSn(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return trimmed
        paren.find(trimmed)?.groupValues?.get(1)?.let { inside ->
            return inside.replace("-", "").uppercase()
        }
        val upper = trimmed.uppercase()
        if (hex16.matches(upper)) {
            return decodeHexVendorSn(upper) ?: upper
        }
        return upper
    }

    private fun decodeHexVendorSn(hex: String): String? {
        return try {
            val bytes = ByteArray(8) { i -> hex.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
            val vendor = String(bytes, 0, 4, Charsets.US_ASCII)
            if (!vendor.all { it.code in 32..126 }) return null
            val suffix = bytes.copyOfRange(4, 8).joinToString("") { b ->
                String.format("%02X", b.toInt() and 0xFF)
            }
            (vendor + suffix).uppercase()
        } catch (_: Exception) {
            null
        }
    }
}
