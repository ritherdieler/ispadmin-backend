package com.dscorp.wispadmin.netdiag.dto

import com.fasterxml.jackson.annotation.JsonProperty
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
    val lastNotifiedAt: Instant?,
    val silencedUntil: Instant? = null
)

data class IncidentsSummaryDto(
    val openCount: Long,
    val p0OpenCount: Long,
    val pollStaleCount: Long
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
    val silencedUntil: Instant? = null,
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

class TrapIngestRequestDto {
    var targetId: Long? = null
    var trapType: String? = null
    var specificType: String? = null
    var sourceHost: String? = null
    var oid: String? = null
    var varBinds: String? = null
    var component: String? = null
    var raw: String? = null
}

data class TrapIngestResponseDto(
    val decisions: List<String>,
    val openedIncidentIds: List<Long>,
    val suppressed: Boolean,
    val trapEventId: Long?,
    val reasonCode: String
)

class SyslogIngestRequestDto {
    var targetId: Long? = null
    var message: String = ""
}

data class SyslogIngestResponseDto(
    val decisions: List<String>,
    val openedIncidentIds: List<Long>,
    val suppressed: Boolean
)

data class MaintenanceWindowDto(
    val id: Long,
    val targetId: Long?,
    val title: String,
    val description: String?,
    val startsAt: Instant,
    val endsAt: Instant,
    val suppressNotifications: Boolean,
    val createdAt: Instant
)

class MaintenanceWindowRequestDto {
    var targetId: Long? = null
    var title: String = ""
    var description: String? = null
    var startsAt: String = ""
    var endsAt: String = ""
}

class SilenceIncidentRequestDto {
    var until: String? = null
    var durationMinutes: Long? = null
}

data class OltLogEventDto(
    val id: Long,
    val receivedAt: Instant,
    val sourceIp: String?,
    val reasonCode: String?,
    val board: Int?,
    val port: Int?,
    val onuIndex: Int?,
    val targetId: Long?,
    val severity: String?,
    val incidentId: Long?,
    val channel: String,
    val alarmIdHex: String?,
    val alarmName: String?,
    val component: String?,
    @get:JsonProperty("isClear")
    val isClear: Boolean,
    @get:JsonProperty("isUnparsed")
    val isUnparsed: Boolean,
    val rawMessage: String
)

data class OltLogPageDto(
    val items: List<OltLogEventDto>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int
)

