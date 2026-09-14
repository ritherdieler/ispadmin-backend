package com.dscorp.wispadmin.wispadmin.service.whatsapp

enum class WhatsAppOutboundMediaKind {
    IMAGE,
    DOCUMENT,
    AUDIO
}

object WhatsAppMediaConstraints {
    const val MAX_IMAGE_BYTES = 5L * 1024 * 1024
    const val MAX_AUDIO_BYTES = 16L * 1024 * 1024
    const val MAX_DOCUMENT_BYTES = 100L * 1024 * 1024
    const val MAX_RETRY_COUNT = 3

    private val imageMimes = setOf("image/jpeg", "image/jpg", "image/png")
    private val audioMimes = setOf(
        "audio/aac",
        "audio/mp4",
        "audio/mpeg",
        "audio/amr",
        "audio/ogg",
        "audio/opus"
    )
    private val documentMimes = setOf(
        "application/pdf",
        "application/msword",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "application/vnd.ms-excel",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "application/vnd.ms-powerpoint",
        "application/vnd.openxmlformats-officedocument.presentationml.presentation",
        "text/plain"
    )

    fun validate(mimeType: String?, sizeBytes: Long, filename: String?): WhatsAppOutboundMediaKind {
        if (sizeBytes <= 0) {
            throw IllegalArgumentException("El archivo esta vacio.")
        }
        val mime = mimeType?.trim()?.lowercase().orEmpty()
        if (mime.isBlank()) {
            throw IllegalArgumentException("Tipo MIME requerido.")
        }
        val kind = kindForMime(mime)
            ?: throw IllegalArgumentException("Tipo de archivo no soportado para envio saliente: $mime")
        val max = maxBytesFor(kind)
        if (sizeBytes > max) {
            throw IllegalArgumentException(
                "El archivo supera el maximo permitido (${max / (1024 * 1024)} MB) para $kind."
            )
        }
        if (filename != null && filename.length > 240) {
            throw IllegalArgumentException("Nombre de archivo demasiado largo.")
        }
        return kind
    }

    fun kindForMime(mimeType: String): WhatsAppOutboundMediaKind? {
        val mime = mimeType.trim().lowercase()
        return when {
            imageMimes.contains(mime) -> WhatsAppOutboundMediaKind.IMAGE
            audioMimes.contains(mime) -> WhatsAppOutboundMediaKind.AUDIO
            documentMimes.contains(mime) -> WhatsAppOutboundMediaKind.DOCUMENT
            else -> null
        }
    }

    fun maxBytesFor(kind: WhatsAppOutboundMediaKind): Long = when (kind) {
        WhatsAppOutboundMediaKind.IMAGE -> MAX_IMAGE_BYTES
        WhatsAppOutboundMediaKind.AUDIO -> MAX_AUDIO_BYTES
        WhatsAppOutboundMediaKind.DOCUMENT -> MAX_DOCUMENT_BYTES
    }

    fun metaType(kind: WhatsAppOutboundMediaKind): String = when (kind) {
        WhatsAppOutboundMediaKind.IMAGE -> "image"
        WhatsAppOutboundMediaKind.DOCUMENT -> "document"
        WhatsAppOutboundMediaKind.AUDIO -> "audio"
    }
}
