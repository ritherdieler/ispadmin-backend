package com.dscorp.wispadmin.wispadmin.service.whatsapp

import com.dscorp.wispadmin.wispadmin.data.model.WhatsAppInboundMessage
import com.dscorp.wispadmin.wispadmin.data.model.WhatsAppMessageLog
import com.dscorp.wispadmin.wispadmin.dto.WhatsAppThreadMessageDto

object WhatsAppThreadMessageMapper {

    fun inboundHasMedia(inbound: WhatsAppInboundMessage): Boolean =
        !inbound.mediaStoredPath.isNullOrBlank() || !inbound.mediaId.isNullOrBlank()

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
        retryCount = null
    )

    fun WhatsAppMessageLog.toThreadMessage(display: WhatsAppTemplateDisplayService) = WhatsAppThreadMessageDto(
        id = "outbound:$id",
        direction = "OUTBOUND",
        body = display.displayStoredMessage(message, messageType),
        messageType = messageType,
        buttonReplyTitle = null,
        hasMedia = outboundHasMedia(this),
        mediaId = id.takeIf { outboundHasMedia(this) },
        mediaMimeType = mediaMimeType,
        mediaFilename = mediaFilename,
        deliveryStatus = deliveryStatus ?: status.takeIf { it.isNotBlank() },
        createdAt = createdAt,
        replyToLogId = replyToLogId,
        operatorUsername = operatorUsername,
        templateCode = resolveTemplateCode(messageType),
        retryCount = retryCount
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
