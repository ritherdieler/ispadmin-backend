package com.dscorp.wispadmin.wispadmin.dto

import com.dscorp.wispadmin.wispadmin.data.model.WhatsAppMessageLog
import java.time.LocalDateTime

data class WhatsAppMessageLogDto(
    val id: Int?,
    val paymentId: Int?,
    val subscriptionId: Int?,
    val phone: String?,
    val messageType: String,
    val templateCode: String,
    val status: String,
    val metaMessageId: String?,
    val deliveryStatus: String?,
    val deliveryStatusAt: LocalDateTime?,
    val sentAt: LocalDateTime?,
    val deliveredAt: LocalDateTime?,
    val readAt: LocalDateTime?,
    val failedAt: LocalDateTime?,
    val conversationId: String?,
    val conversationCategory: String?,
    val billable: Boolean?,
    val pricingModel: String?,
    val campaignId: String?,
    val operatorUsername: String?,
    val operatorName: String?,
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
        templateCode = messageType,
        status = status,
        metaMessageId = metaMessageId,
        deliveryStatus = deliveryStatus,
        deliveryStatusAt = deliveryStatusAt,
        sentAt = sentAt,
        deliveredAt = deliveredAt,
        readAt = readAt,
        failedAt = failedAt,
        conversationId = conversationId,
        conversationCategory = conversationCategory,
        billable = billable,
        pricingModel = pricingModel,
        campaignId = campaignId,
        operatorUsername = operatorUsername,
        operatorName = operatorUsername,
        errorMessage = errorMessage,
        createdAt = createdAt
    )
}
