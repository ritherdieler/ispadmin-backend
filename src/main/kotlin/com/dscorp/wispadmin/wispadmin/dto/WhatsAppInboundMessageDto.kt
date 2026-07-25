package com.dscorp.wispadmin.wispadmin.dto

import com.dscorp.wispadmin.wispadmin.data.model.WhatsAppInboundMessage
import java.time.LocalDateTime

data class WhatsAppInboundMessageDto(
    val id: Int?,
    val metaMessageId: String,
    val phone: String,
    val body: String?,
    val messageType: String,
    val subscriptionId: Int?,
    val clientName: String?,
    val identified: Boolean,
    val autoReplySent: Boolean,
    val buttonReplyId: String?,
    val buttonReplyTitle: String?,
    val mediaId: String?,
    val mediaMimeType: String?,
    val hasMedia: Boolean,
    val contextMessageId: String?,
    val replyToLogId: Int?,
    val readAt: LocalDateTime?,
    val errorMessage: String?,
    val createdAt: LocalDateTime
)

fun WhatsAppInboundMessage.toDto(clientName: String? = null) = WhatsAppInboundMessageDto(
    id = id,
    metaMessageId = metaMessageId,
    phone = phone,
    body = messageText,
    messageType = messageType,
    subscriptionId = subscriptionId,
    clientName = clientName,
    identified = subscriptionId != null,
    autoReplySent = replySent,
    buttonReplyId = buttonReplyId,
    buttonReplyTitle = buttonReplyTitle,
    mediaId = mediaId,
    mediaMimeType = mediaMimeType,
    hasMedia = !mediaStoredPath.isNullOrBlank(),
    contextMessageId = contextMessageId,
    replyToLogId = replyToLogId,
    readAt = readAt,
    errorMessage = errorMessage,
    createdAt = createdAt
)
