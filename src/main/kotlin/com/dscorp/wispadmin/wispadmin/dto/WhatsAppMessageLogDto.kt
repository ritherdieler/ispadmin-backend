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
        errorMessage = errorMessage,
        createdAt = createdAt
    )
}