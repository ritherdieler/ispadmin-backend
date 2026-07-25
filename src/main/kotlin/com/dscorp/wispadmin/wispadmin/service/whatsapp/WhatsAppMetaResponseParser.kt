package com.dscorp.wispadmin.wispadmin.service.whatsapp

import com.fasterxml.jackson.databind.ObjectMapper

object WhatsAppMetaResponseParser {

    private val objectMapper = ObjectMapper()

    fun extractMessageId(metaResponse: String): String? {
        if (metaResponse.isBlank()) return null
        return try {
            val root = objectMapper.readTree(metaResponse)
            root.path("messages").path(0).path("id").asText(null)
        } catch (_: Exception) {
            null
        }
    }
}
