package com.dscorp.wispadmin.wispadmin.service.whatsapp

import com.dscorp.wispadmin.wispadmin.data.model.WhatsAppInboundMessage
import com.dscorp.wispadmin.wispadmin.data.model.WhatsAppMessageLog
import com.dscorp.wispadmin.wispadmin.dto.WhatsAppThreadMessageDto

object WhatsAppThreadMessageMapper {

    /**
     * Reactions must never appear as standalone thread bubbles (historical orphans included).
     */
    fun isRenderableInbound(inbound: WhatsAppInboundMessage): Boolean {
        val type = inbound.messageType.trim()
        if (type.equals("reaction", ignoreCase = true)) return false
        val text = inbound.messageText?.trim().orEmpty()
        if (text.equals("[reaction]", ignoreCase = true)) return false
        return true
    }

    fun isRenderableThreadMessage(dto: WhatsAppThreadMessageDto): Boolean {
        val type = dto.messageType.trim()
        if (type.equals("reaction", ignoreCase = true)) return false
        val body = dto.body?.trim().orEmpty()
        if (body.equals("[reaction]", ignoreCase = true)) return false
        return true
    }

    fun inboundHasMedia(inbound: WhatsAppInboundMessage): Boolean =
        !inbound.mediaStoredPath.isNullOrBlank() || !inbound.mediaId.isNullOrBlank()

    fun inboundIsPaymentProof(inbound: WhatsAppInboundMessage): Boolean {
        val type = inbound.messageType.trim().lowercase()
        if (type == "image") return true
        if (type != "document") return false
        val mime = inbound.mediaMimeType
            ?.substringBefore(';')
            ?.trim()
            ?.lowercase()
            .orEmpty()
        return mime == "application/pdf"
    }

    fun WhatsAppInboundMessage.toThreadMessage() = WhatsAppThreadMessageDto(
        id = "inbound:$id",
        direction = "INBOUND",
        body = messageText,
        messageType = messageType,
        buttonReplyTitle = buttonReplyTitle,
        hasMedia = inboundHasMedia(this),
        mediaId = id,
        mediaMimeType = mediaMimeType,
        mediaFilename = null,
        deliveryStatus = null,
        createdAt = createdAt,
        replyToLogId = replyToLogId,
        operatorUsername = null,
        templateCode = null,
        retryCount = null,
        reactionEmoji = agentReactionEmoji,
        editedAt = null,
        deletedAt = null,
        metaMessageId = metaMessageId?.takeIf { it.isNotBlank() },
    )

    fun WhatsAppMessageLog.toThreadMessage(display: WhatsAppTemplateDisplayService) = WhatsAppThreadMessageDto(
        id = "outbound:$id",
        direction = "OUTBOUND",
        body = if (deletedAt != null) null else display.displayStoredMessage(message, messageType),
        messageType = messageType,
        buttonReplyTitle = null,
        hasMedia = outboundHasMedia(this) && deletedAt == null,
        mediaId = id.takeIf { outboundHasMedia(this) && deletedAt == null },
        mediaMimeType = mediaMimeType,
        mediaFilename = mediaFilename,
        deliveryStatus = when {
            deletedAt != null -> "DELETED"
            else -> deliveryStatus ?: status.takeIf { it.isNotBlank() }
        },
        createdAt = createdAt,
        replyToLogId = replyToLogId,
        operatorUsername = operatorUsername,
        templateCode = resolveTemplateCode(messageType),
        retryCount = retryCount,
        reactionEmoji = customerReactionEmoji,
        editedAt = editedAt,
        deletedAt = deletedAt,
        metaMessageId = metaMessageId?.takeIf { it.isNotBlank() },
    )

    fun outboundHasMedia(log: WhatsAppMessageLog): Boolean =
        !log.mediaStoredPath.isNullOrBlank() || !log.mediaMetaId.isNullOrBlank()

    private fun resolveTemplateCode(messageType: String): String? {
        val normalized = messageType.trim().uppercase()
        return when (normalized) {
            "AUTO_REPLY", "OPERATOR_REPLY", "OPERATOR_MEDIA", "TEXT", "INTERACTIVE",
            "IMAGE", "DOCUMENT", "AUDIO" -> null
            else -> messageType.takeIf { it.isNotBlank() }
        }
    }
}
