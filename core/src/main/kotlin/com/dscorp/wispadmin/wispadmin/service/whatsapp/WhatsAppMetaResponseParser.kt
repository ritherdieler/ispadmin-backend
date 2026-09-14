package com.dscorp.wispadmin.wispadmin.service.whatsapp

import com.fasterxml.jackson.databind.ObjectMapper

object WhatsAppMetaResponseParser {

    private val objectMapper = ObjectMapper()

    fun extractMessageId(metaResponse: String): String? {
        if (metaResponse.isBlank()) return null
        return try {
            val root = objectMapper.readTree(metaResponse)
            val fromMessages = root.path("messages").takeIf { it.isArray && it.size() > 0 }
                ?.path(0)
                ?.path("id")
                ?.asText(null)
                ?.takeIf { it.isNotBlank() }
            if (fromMessages != null) return fromMessages
            root.path("message_id").asText(null)?.takeIf { it.isNotBlank() }
                ?: root.path("id").asText(null)?.takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }
}
