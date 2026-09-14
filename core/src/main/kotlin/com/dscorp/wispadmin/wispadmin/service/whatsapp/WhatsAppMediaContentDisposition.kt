package com.dscorp.wispadmin.wispadmin.service.whatsapp

object WhatsAppMediaContentDisposition {

    fun forMimeType(mimeType: String?, filename: String): String {
        val safeName = filename.replace("\"", "")
        val mime = mimeType?.lowercase()?.trim().orEmpty()
        val baseMime = mime.substringBefore(';').trim()
        val disposition =
            if (baseMime.startsWith("image/") || baseMime.startsWith("audio/")) "inline" else "attachment"
        return "$disposition; filename=\"$safeName\""
    }
}
