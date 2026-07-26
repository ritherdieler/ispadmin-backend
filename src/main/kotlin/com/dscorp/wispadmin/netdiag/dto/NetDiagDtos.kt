package com.dscorp.wispadmin.netdiag.dto

import java.time.Instant

data class NetDiagHealthResponseDto(
    val status: String,
    val module: String
)

data class ErrorResponseDto(
    val error: String,
    val message: String
)

data class IncidentSummaryDto(
    val id: Long,
    val targetId: Long?,
    val dedupKey: String,
    val status: String,
    val severity: String,
    val title: String,
    val reasonCode: String?,
    val openedAt: Instant
)

data class IncidentDetailDto(
    val id: Long,
    val targetId: Long?,
    val dedupKey: String,
    val status: String,
    val severity: String,
    val title: String,
    val reasonCode: String?,
    val openedAt: Instant,
    val acknowledgedAt: Instant?,
    val resolvedAt: Instant?,
    val events: List<IncidentEventDto>
)

data class IncidentEventDto(
    val id: Long,
    val type: String,
    val payload: String?,
    val createdAt: Instant
)
