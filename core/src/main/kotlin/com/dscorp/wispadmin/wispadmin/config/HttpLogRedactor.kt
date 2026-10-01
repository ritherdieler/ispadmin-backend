package com.dscorp.wispadmin.wispadmin.config

object HttpLogRedactor {
    const val OMITTED = "[omitted]"
    private const val MASK = "***"
    private const val SENSITIVE = "password|passwd|passphrase|secret|token|authorization|api[_-]?key|dni|phone|ruc"
    private val JSON_FIELD = Regex(
        "(?i)\"([A-Za-z0-9_]*(?:$SENSITIVE)[A-Za-z0-9_]*)\"\\s*:\\s*(\"(?:\\\\.|[^\"\\\\])*\"|-?\\d+(?:\\.\\d+)?)"
    )
    private val FORM_FIELD = Regex("(?i)\\b([A-Za-z0-9_]*(?:$SENSITIVE)[A-Za-z0-9_]*=)[^&\\s\"]*")
    private val OMITTED_PATHS = listOf("/subscription", "/onu-registration-operations")

    fun redactBody(body: String): String = body
        .replace(JSON_FIELD) { "\"${it.groupValues[1]}\":\"$MASK\"" }
        .replace(FORM_FIELD) { "${it.groupValues[1]}$MASK" }

    fun bodyForLog(uri: String, body: String): String {
        if (body.isEmpty()) return body
        val path = uri.substringAfter("/ispadmin-staging").substringAfter("/ispadmin")
        return if (OMITTED_PATHS.any { path.startsWith(it) }) OMITTED else redactBody(body)
    }

    fun authorization(header: String?): String {
        val value = header?.trim().orEmpty()
        if (value.isEmpty()) return ""
        return "${value.substringBefore(' ')} $MASK"
    }
}
