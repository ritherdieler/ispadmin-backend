package com.dscorp.wispadmin.wispadmin.service.whatsapp

import com.fasterxml.jackson.databind.JsonNode

data class WhatsAppInboundPayload(
    val metaMessageId: String,
    val phone: String,
    val messageType: String,
    val messageText: String?,
    val buttonReplyId: String? = null,
    val buttonReplyTitle: String? = null,
    val mediaId: String? = null,
    val mediaMimeType: String? = null,
    val contextMessageId: String? = null
)

object WhatsAppInboundPayloadParser {

    fun parse(message: JsonNode): WhatsAppInboundPayload? {
        val wamid = message.path("id").asText(null) ?: return null
        val phone = message.path("from").asText(null) ?: return null
        val messageType = message.path("type").asText("text")
        val contextMessageId = message.path("context").path("id").asText(null)

        return when (messageType) {
            "text" -> WhatsAppInboundPayload(
                metaMessageId = wamid,
                phone = phone,
                messageType = messageType,
                messageText = message.path("text").path("body").asText(null),
                contextMessageId = contextMessageId
            )
            "interactive" -> {
                val interactive = message.path("interactive")
                val buttonReply = interactive.path("button_reply")
                val listReply = interactive.path("list_reply")
                val reply = if (!buttonReply.isMissingNode && !buttonReply.isNull) buttonReply else listReply
                WhatsAppInboundPayload(
                    metaMessageId = wamid,
                    phone = phone,
                    messageType = "button_reply",
                    messageText = reply.path("title").asText(null),
                    buttonReplyId = reply.path("id").asText(null),
                    buttonReplyTitle = reply.path("title").asText(null),
                    contextMessageId = contextMessageId
                )
            }
            "button" -> {
                val button = message.path("button")
                WhatsAppInboundPayload(
                    metaMessageId = wamid,
                    phone = phone,
                    messageType = "button_reply",
                    messageText = button.path("text").asText(null),
                    buttonReplyId = button.path("payload").asText(null),
                    buttonReplyTitle = button.path("text").asText(null),
                    contextMessageId = contextMessageId
                )
            }
            "image" -> WhatsAppInboundPayload(
                metaMessageId = wamid,
                phone = phone,
                messageType = messageType,
                messageText = message.path("image").path("caption").asText(null),
                mediaId = message.path("image").path("id").asText(null),
                mediaMimeType = message.path("image").path("mime_type").asText("image/jpeg"),
                contextMessageId = contextMessageId
            )
            "document" -> WhatsAppInboundPayload(
                metaMessageId = wamid,
                phone = phone,
                messageType = messageType,
                messageText = message.path("document").path("filename").asText(null),
                mediaId = message.path("document").path("id").asText(null),
                mediaMimeType = message.path("document").path("mime_type").asText("application/octet-stream"),
                contextMessageId = contextMessageId
            )
            else -> WhatsAppInboundPayload(
                metaMessageId = wamid,
                phone = phone,
                messageType = messageType,
                messageText = null,
                contextMessageId = contextMessageId
            )
        }
    }
}
