package com.dscorp.wispadmin.wispadmin.service.whatsapp

object WhatsAppMediaContentDisposition {

    fun forMimeType(mimeType: String?, filename: String): String {
        val safeName = filename.replace("\"", "")
        val mime = mimeType?.lowercase()?.trim().orEmpty()
        val disposition = if (mime.startsWith("image/") || mime.startsWith("audio/")) "inline" else "attachment"
        return "$disposition; filename=\"$safeName\""
    }
}
