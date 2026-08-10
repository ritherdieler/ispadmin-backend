package com.dscorp.wispadmin.wispadmin.dto

import java.time.LocalDateTime

data class CrmConversationDto(
    val id: Long,
    val channel: String,
    val phone: String,
    val subscriptionId: Int?,
    val status: String,
    val assignedAgentId: Int?,
    val assignedAgentName: String?,
    val resolvedByAgentId: Int? = null,
    val resolvedByAgentName: String? = null,
    val priority: Int,
    val claimedAt: LocalDateTime?,
    val resolvedAt: LocalDateTime?,
    val lastInboundAt: LocalDateTime?,
    val lastOutboundAt: LocalDateTime?,
    val handoffSummary: String? = null,
    val botPaused: Boolean? = null,
    val version: Long,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
)

data class CrmAssignmentEventDto(
    val id: Long,
    val conversationId: Long,
    val eventType: String,
    val fromUserId: Int?,
    val fromUserName: String?,
    val toUserId: Int?,
    val toUserName: String?,
    val note: String?,
    val createdAt: LocalDateTime
)

data class CrmInternalNoteDto(
    val id: Long,
    val conversationId: Long,
    val authorId: Int,
    val authorName: String?,
    val text: String,
    val createdAt: LocalDateTime
)

data class CrmTransferBody(
    val toAgentId: Int,
    val note: String
)

data class CrmResolveBody(
    val note: String? = null,
    val resumeBot: Boolean = true
)

data class CrmInternalNoteBody(
    val text: String
)

data class CrmReopenBody(
    val note: String? = null
)

data class CrmReleaseBody(
    val note: String? = null
)
