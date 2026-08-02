package com.dscorp.wispadmin.wispadmin.dto

import com.dscorp.wispadmin.wispadmin.data.model.AssistanceTicketStatus
import java.time.LocalDateTime

data class CrmTicketDto(
    val id: Int,
    val phone: String,
    val category: String,
    val description: String,
    val status: AssistanceTicketStatus,
    val priority: String,
    val createdAt: LocalDateTime?,
    val assignedAt: LocalDateTime?,
    val resolvedAt: LocalDateTime?,
    val closedAt: LocalDateTime?,
    val assignedTo: String?,
    val subscriptionId: Int?,
    val conversationId: Long?,
    val slaBreached: Boolean,
    val clientName: String?
)

data class CrmCreateTicketFromConversationBody(
    val category: String,
    val description: String,
    val customerName: String? = null
)

data class CrmCreateTicketResultDto(
    val ticket: CrmTicketDto,
    val deduplicated: Boolean
)

data class CrmTicketStatusSummaryDto(
    val ticketId: Int,
    val status: String,
    val statusLabel: String,
    val category: String,
    val assignedTo: String?,
    val createdAt: LocalDateTime?,
    val slaBreached: Boolean
)
