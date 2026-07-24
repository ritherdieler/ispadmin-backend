package com.dscorp.wispadmin.wispadmin.dto

import com.dscorp.wispadmin.wispadmin.data.model.WhatsAppMessageLog
import java.time.LocalDateTime

data class WhatsAppMessageLogDto(
    val id: Int?,
    val paymentId: Int?,
    val subscriptionId: Int?,
    val phone: String?,
    val messageType: String,
    val status: String,
    val metaMessageId: String?,
    val deliveryStatus: String?,
    val deliveryStatusAt: LocalDateTime?,
    val errorMessage: String?,
    val createdAt: LocalDateTime
)

fun WhatsAppMessageLog.toDto(): WhatsAppMessageLogDto {
    return WhatsAppMessageLogDto(
        id = id,
        paymentId = paymentId,
        subscriptionId = subscriptionId,
        phone = phone,
        messageType = messageType,
        status = status,
        metaMessageId = metaMessageId,
        deliveryStatus = deliveryStatus,
        deliveryStatusAt = deliveryStatusAt,
        errorMessage = errorMessage,
        createdAt = createdAt
    )
}