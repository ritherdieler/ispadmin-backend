package com.dscorp.wispadmin.wispadmin.service

import com.dscorp.wispadmin.wispadmin.config.WhatsAppProperties
import org.springframework.stereotype.Component
import java.nio.file.Paths

@Component
class PaymentProofPathResolver(
    private val whatsAppProperties: WhatsAppProperties,
) {

    fun toStoredFilename(rawPath: String?): String? {
        if (rawPath.isNullOrBlank()) {
            return null
        }
        val filename = Paths.get(rawPath.trim()).fileName?.toString()?.trim().orEmpty()
        return filename.takeIf { it.isNotBlank() }
    }

    fun toPublicPath(stored: String?): String? {
        val filename = toStoredFilename(stored) ?: return null
        val base = whatsAppProperties.resolvedMediaBasePath().trimEnd('/', '\\')
        if (base.isBlank()) {
            return filename
        }
        return Paths.get(base, filename).normalize().toString()
    }
}
