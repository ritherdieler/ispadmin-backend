package com.dscorp.wispadmin.oltgateway.dto

data class HealthResponseDto(
    val status: String,
    val oltReachable: Boolean,
    val latencyMs: Long
)

data class OltInfoDto(
    val oltId: String,
    val product: String,
    val version: String,
    val patch: String?,
    val uptime: String?,
    val boards: List<BoardInfoDto>,
    val modelCode: String? = null,
    val maxConcurrentCliSessions: Int? = null
)

data class BoardInfoDto(
    val slot: Int,
    val boardName: String,
    val status: String
)

data class OnuSummaryListDto(
    val items: List<OnuSummaryItemDto>,
    val total: Int
)

data class OnuSummaryItemDto(
    val frame: Int,
    val slot: Int,
    val port: Int,
    val ontId: Int,
    val sn: String,
    val controlFlag: String?,
    val runState: String?,
    val configState: String?,
    val matchState: String?,
    val description: String? = null
)

data class OnuDetailDto(
    val sn: String,
    val frame: Int,
    val slot: Int,
    val port: Int,
    val ontId: Int,
    val description: String?,
    val runState: String?,
    val controlFlag: String?,
    val lineProfileId: Int?,
    val lineProfileName: String?,
    val serviceProfileId: Int?,
    val serviceProfileName: String?
)

data class OpticalInfoDto(
    val slot: Int,
    val port: Int,
    val ontId: Int,
    val rxPowerDbm: Double?,
    val txPowerDbm: Double?,
    val temperatureC: Double?,
    val voltageV: Double?,
    val biasCurrentMa: Double?,
    val oltRxPowerDbm: Double? = null
)

data class ErrorResponseDto(
    val error: String,
    val message: String
)

data class SyncResultDto(
    val inserted: Int,
    val updated: Int,
    val softDeleted: Int,
    val unchanged: Int,
    val durationMs: Long,
    val skippedReason: String? = null,
    val error: String? = null
)

data class SignalPollResultDto(
    val slotsPolled: Int,
    val portsPolled: Int,
    val onusUpdated: Int,
    val durationMs: Long,
    val skippedReason: String? = null,
    val error: String? = null
)

data class SignalPollStatusDto(
    val running: Boolean,
    val lastStartedAt: String? = null,
    val lastResult: SignalPollResultDto? = null,
    val busQueueDepth: Int = 0,
    val busBusyJobType: String? = null
)

data class SyncStatusDto(
    val running: Boolean,
    val lastStartedAt: String? = null,
    val lastResult: SyncResultDto? = null,
    val signalRunning: Boolean = false,
    val signalLastStartedAt: String? = null,
    val signalLastResult: SignalPollResultDto? = null,
    val busQueueDepth: Int = 0,
    val busBusyJobType: String? = null
)

data class ConfiguredOnuItemDto(
    val id: Long,
    val sn: String,
    val externalId: String,
    val board: Int,
    val port: Int,
    val onuIndex: Int,
    val name: String?,
    val importedFromOlt: Boolean,
    val runState: String?,
    val matchState: String?,
    val polledAt: String?,
    val onuRxDbm: Double? = null,
    val onuTxDbm: Double? = null,
    val oltRxDbm: Double? = null,
    val signalCategory: String? = null
)

data class ConfiguredOnuPageDto(
    val items: List<ConfiguredOnuItemDto>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int
)
