package com.dscorp.wispadmin.wispadmin.dto

import java.time.LocalDateTime

data class WhatsAppConversationSummaryDto(
    val phone: String,
    val clientName: String?,
    val subscriptionId: Int?,
    val lastMessagePreview: String?,
    val lastMessageAt: LocalDateTime,
    val lastInboundAt: LocalDateTime? = null,
    val lastOutboundAt: LocalDateTime? = null,
    val unreadCount: Int,
    val identified: Boolean,
    val serviceWindowActive: Boolean,
    val serviceWindowExpiresAt: LocalDateTime?,
    val lastButtonReplyId: String? = null,
    val lastHasMedia: Boolean = false,
    val hasPendingReceipt: Boolean = false,
    val crmConversationId: Long? = null,
    val crmStatus: String? = null,
    val assignedAgentId: Int? = null,
    val resolvedByAgentId: Int? = null,
    val resolvedByAgentName: String? = null,
    val crmResolvedAt: LocalDateTime? = null
)

data class WhatsAppConversationPageDto(
    val items: List<WhatsAppConversationSummaryDto>,
    val hasMore: Boolean,
    val nextCursor: LocalDateTime? = null
)

data class WhatsAppInboxViewCountsDto(
    val queue: Long,
    val mine: Long,
    val team: Long,
    val receipts: Long,
    val resolved: Long,
    val all: Long,
    val totalUnread: Long,
    val conversationsWithUnread: Long
)

data class WhatsAppThreadMessageDto(
    val id: String,
    val direction: String,
    val body: String?,
    val messageType: String,
    val buttonReplyTitle: String?,
    val hasMedia: Boolean,
    val mediaExpired: Boolean = false,
    val mediaId: Int?,
    val mediaMimeType: String? = null,
    val mediaFilename: String? = null,
    val deliveryStatus: String?,
    val createdAt: LocalDateTime,
    val replyToLogId: Int?,
    val operatorUsername: String?,
    val operatorDisplayName: String? = null,
    val templateCode: String? = null,
    val retryCount: Int? = null,
    val reactionEmoji: String? = null,
    val editedAt: LocalDateTime? = null,
    val deletedAt: LocalDateTime? = null,
    val metaMessageId: String? = null,
) {
    /** Alias for Meta Cloud API message id (same as [metaMessageId]). */
    @get:com.fasterxml.jackson.annotation.JsonProperty("wamid")
    val wamid: String?
        get() = metaMessageId?.takeIf { it.isNotBlank() }
}

data class WhatsAppThreadPageDto(
    val messages: List<WhatsAppThreadMessageDto>,
    val hasMore: Boolean,
    val nextBefore: LocalDateTime?
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

data class WhatsAppConversationRecentPaymentDto(
    val id: Int,
    val amount: Double,
    val paid: Boolean,
    val billingDate: LocalDateTime?,
    val paymentDate: LocalDateTime?
)

data class WhatsAppConversationInstallationOrderDto(
    val id: Int,
    val status: String,
    val scheduledDate: LocalDateTime?,
    val createdAt: LocalDateTime?
)

data class WhatsAppConversationHistoryItemDto(
    val conversationId: Long,
    val phone: String,
    val status: String,
    val lastInboundAt: LocalDateTime?,
    val lastOutboundAt: LocalDateTime?
)

data class WhatsAppConversationTicketItemDto(
    val id: Int,
    val category: String,
    val status: String,
    val statusLabel: String,
    val priority: String,
    val createdAt: LocalDateTime?,
    val assignedTo: String?,
    val conversationId: Long?,
    val slaBreached: Boolean
)

data class WhatsAppConversationContextDto(
    val phone: String,
    val clientName: String?,
    val identified: Boolean,
    val subscription: WhatsAppConversationSubscriptionDto?,
    val pendingDebt: WhatsAppConversationPendingDebtDto?,
    val recentLogs: List<WhatsAppMessageLogDto>,
    val serviceWindowActive: Boolean,
    val serviceWindowExpiresAt: LocalDateTime?,
    val recentPayments: List<WhatsAppConversationRecentPaymentDto> = emptyList(),
    val installationOrders: List<WhatsAppConversationInstallationOrderDto> = emptyList(),
    val conversationHistory: List<WhatsAppConversationHistoryItemDto> = emptyList(),
    val tickets: List<WhatsAppConversationTicketItemDto> = emptyList()
)

data class WhatsAppMarkAllReadResultDto(
    val phone: String,
    val markedCount: Int,
    val success: Boolean
)
