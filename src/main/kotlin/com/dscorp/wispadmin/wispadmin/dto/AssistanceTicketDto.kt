package com.dscorp.wispadmin.wispadmin.dto

import com.dscorp.wispadmin.wispadmin.data.model.AssistanceTicketStatus
import java.util.*

data class AssistanceTicketDto(
    val id: Int = 0,
    val name: String,
    val phone: String = "",
    val ip: String? = null,
    val category: String,
    val description: String,
    var status: AssistanceTicketStatus? = AssistanceTicketStatus.PENDING,
    val comments: String? = null,
    var priority: String? = null,
    var createdAt: Date = Date(),
    var scheduledAt: Date? = null,
    var assignedAt: Date? = null,
    val resolvedAt: Date? = null,
    var closedAt: Date? = null,
    val assignedTo: String? = null,
    val place: String? = null,
    val address: String? = null,
    val sheetImageUrl: String?,
    val isExternalCustomer: Boolean = false,
    )
