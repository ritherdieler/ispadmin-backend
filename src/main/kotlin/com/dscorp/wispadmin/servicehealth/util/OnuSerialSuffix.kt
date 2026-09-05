package com.dscorp.wispadmin.servicehealth.util

object OnuSerialSuffix {
    private val HEX_SUFFIX = Regex("^[0-9A-F]{6}$")

    fun normalizeSuffix(serial: String?): String? {
        val cleaned = serial
            ?.uppercase()
            ?.filter { it.isLetterOrDigit() }
            ?.takeIf { it.isNotBlank() }
            ?: return null
        if (cleaned.length < 6) return null
        val suffix = cleaned.takeLast(6)
        return suffix.takeIf { HEX_SUFFIX.matches(it) }
    }
}
