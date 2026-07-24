package com.dscorp.wispadmin.wispadmin.dto

import com.dscorp.wispadmin.wispadmin.data.model.WhatsAppInboundMessage
import java.time.LocalDateTime

data class WhatsAppInboundMessageDto(
    val id: Int?,
    val metaMessageId: String,
    val phone: String,
    val messageText: String?,
    val messageType: String,
    val subscriptionId: Int?,
    val processed: Boolean,
    val replySent: Boolean,
    val errorMessage: String?,
    val createdAt: LocalDateTime
)

fun WhatsAppInboundMessage.toDto() = WhatsAppInboundMessageDto(
    id = id,
    metaMessageId = metaMessageId,
    phone = phone,
    messageText = messageText,
    messageType = messageType,
    subscriptionId = subscriptionId,
    processed = processed,
    replySent = replySent,
    errorMessage = errorMessage,
    createdAt = createdAt
)
