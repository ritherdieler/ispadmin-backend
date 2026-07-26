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
    val targetName: String?,
    val dedupKey: String,
    val status: String,
    val severity: String,
    val title: String,
    val reasonCode: String?,
    val openedAt: Instant,
    val lastNotifiedAt: Instant?
)

data class IncidentDetailDto(
    val id: Long,
    val targetId: Long?,
    val targetName: String?,
    val dedupKey: String,
    val status: String,
    val severity: String,
    val title: String,
    val reasonCode: String?,
    val openedAt: Instant,
    val acknowledgedAt: Instant?,
    val resolvedAt: Instant?,
    val lastNotifiedAt: Instant?,
    val events: List<IncidentEventDto>
)

data class IncidentEventDto(
    val id: Long,
    val type: String,
    val payload: String?,
    val createdAt: Instant
)

class AlertIngestRequestDto {
    var targetId: Long? = null
    var reasonCode: String = ""
    var severity: String = "P0"
    var title: String = ""
    var component: String = "ingest"
    var details: String? = null
}

data class AlertIngestResponseDto(
    val decisions: List<String>,
    val openedIncidentIds: List<Long>,
    val suppressed: Boolean
)

