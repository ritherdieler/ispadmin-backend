package com.dscorp.wispadmin.wispadmin.dto

import java.time.LocalDateTime

data class WhatsAppConversationSummaryDto(
    val phone: String,
    val clientName: String?,
    val subscriptionId: Int?,
    val lastMessagePreview: String?,
    val lastMessageAt: LocalDateTime,
    val unreadCount: Int,
    val identified: Boolean,
    val serviceWindowActive: Boolean,
    val serviceWindowExpiresAt: LocalDateTime?,
    val lastButtonReplyId: String? = null,
    val lastHasMedia: Boolean = false
)

data class WhatsAppThreadMessageDto(
    val id: String,
    val direction: String,
    val body: String?,
    val messageType: String,
    val buttonReplyTitle: String?,
    val hasMedia: Boolean,
    val mediaId: Int?,
    val deliveryStatus: String?,
    val createdAt: LocalDateTime,
    val replyToLogId: Int?,
    val operatorUsername: String?,
    val templateCode: String? = null
)

data class WhatsAppConversationSubscriptionDto(
    val id: Int,
    val status: String?,
    val planName: String?
)

data class WhatsAppConversationPendingDebtDto(
    val amount: Double,
    val invoiceCount: Int?
)

data class WhatsAppConversationContextDto(
    val phone: String,
    val clientName: String?,
    val identified: Boolean,
    val subscription: WhatsAppConversationSubscriptionDto?,
    val pendingDebt: WhatsAppConversationPendingDebtDto?,
    val recentLogs: List<WhatsAppMessageLogDto>,
    val serviceWindowActive: Boolean,
    val serviceWindowExpiresAt: LocalDateTime?
)

data class WhatsAppMarkAllReadResultDto(
    val phone: String,
    val markedCount: Int,
    val success: Boolean
)
