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
    val oltRxPowerDbm: Double? = null,
    val distanceM: Int? = null,
    val matchState: String? = null
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

/**
 * Respuesta de los POST de sync, que ya no bloquean el hilo HTTP.
 * Siempre 200: `started=false` con `skippedReason` si el job ya estaba corriendo.
 */
data class SyncJobStatusDto(
    val job: String,
    val started: Boolean,
    val running: Boolean,
    val skippedReason: String? = null,
    val lastStartedAt: String? = null,
    val lastResult: SyncResultDto? = null,
    val lastSignalResult: SignalPollResultDto? = null
)

data class AutofindRefreshResultDto(
    val source: String,
    val seen: Int = 0,
    val stored: Int = 0,
    val removed: Int = 0,
    val durationMs: Long = 0,
    val skippedReason: String? = null,
    val error: String? = null
)

data class AutofindStatusDto(
    val running: Boolean,
    val cachedCount: Long,
    val lastRefreshAt: String? = null,
    val lastResult: AutofindRefreshResultDto? = null
)

data class ConfiguredOnuFilter(
    val q: String? = null,
    val board: Int? = null,
    val port: Int? = null,
    val oltId: Long? = null,
    val zoneId: Long? = null,
    val vlan: Int? = null,
    val onuTypeId: Long? = null,
    val onuTypeName: String? = null,
    val customProfile: String? = null,
    val ponType: String? = null,
    val mode: String? = null,
    val status: String? = null,
    val runState: String? = null,
    val signalCategory: String? = null,
    val splitterId: Long? = null,
    val configurationMethod: String? = null,
    val wanMode: String? = null,
    val mgmtIpMode: String? = null,
    val importedSynced: Boolean? = null,
    val lastResyncFailed: Boolean? = null,
    val lineProfileMaptype: String? = null,
    val administrativeStatus: String? = null,
    val lastDownCause: String? = null
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
    val signalCategory: String? = null,
    val oltId: Long? = null,
    val oltName: String? = null,
    val zoneName: String? = null,
    val splitterId: Long? = null,
    val mode: String? = null,
    val vlan: Int? = null,
    val onuTypeName: String? = null,
    val authorizationDate: String? = null,
    val administrativeStatus: String? = null,
    val lastDownCause: String? = null,
    val hasVoip: Boolean = false,
    val hasTv: Boolean = false,
    val ponType: String? = null,
    val customProfile: String? = null,
    val syncedAfterImport: Boolean = true,
    val lastResyncFailed: Boolean = false,
    val configurationMethod: String? = null,
    val wanMode: String? = null,
    val mgmtIpMode: String? = null,
    val ipAddress: String? = null,
    val address: String? = null,
    val contact: String? = null
)

data class ConfiguredOnuServicePortDto(
    val userVlanId: Int,
    val cvlanId: Int? = null,
    val svlanId: Int? = null,
    val tagTransform: String,
    val downloadSpeed: String? = null,
    val uploadSpeed: String? = null,
    val oltServicePortId: Int? = null
)

data class ConfiguredOnuDetailDto(
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
    val signalCategory: String? = null,
    val oltId: Long? = null,
    val oltName: String? = null,
    val zoneName: String? = null,
    val splitterId: Long? = null,
    val splitterPort: Int? = null,
    val mode: String? = null,
    val vlan: Int? = null,
    val onuTypeName: String? = null,
    val authorizationDate: String? = null,
    val administrativeStatus: String? = null,
    val lastDownCause: String? = null,
    val hasVoip: Boolean = false,
    val hasTv: Boolean = false,
    val ponType: String? = null,
    val gponChannel: String? = null,
    val customProfile: String? = null,
    val syncedAfterImport: Boolean = true,
    val lastResyncFailed: Boolean = false,
    val configurationMethod: String? = null,
    val wanMode: String? = null,
    val mgmtIpMode: String? = null,
    val mgmtVlanId: Int? = null,
    val mgmtIpAddress: String? = null,
    val tr069Profile: String? = null,
    val ipAddress: String? = null,
    val subnetMask: String? = null,
    val defaultGateway: String? = null,
    val dns1: String? = null,
    val dns2: String? = null,
    val address: String? = null,
    val contact: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val lineProfileName: String? = null,
    val serviceProfileName: String? = null,
    val lastStatusChange: String? = null,
    val temperatureC: Double? = null,
    val distanceM: Int? = null,
    val ethernetPortCount: Int = 0,
    val wifiPortCount: Int = 0,
    val servicePorts: List<ConfiguredOnuServicePortDto> = emptyList()
)

data class ConfiguredOnuLiveStatusDto(
    val sn: String? = null,
    val runState: String? = null,
    val matchState: String? = null,
    val controlFlag: String? = null,
    val description: String? = null,
    val onuRxDbm: Double? = null,
    val onuTxDbm: Double? = null,
    val oltRxDbm: Double? = null,
    val temperatureC: Double? = null,
    val voltageV: Double? = null,
    val biasCurrentMa: Double? = null,
    val distanceM: Int? = null,
    val lineProfileName: String? = null,
    val serviceProfileName: String? = null
)

data class ConfiguredOnuHistoryItemDto(
    val id: Long,
    val action: String,
    val userId: Long? = null,
    val ipAddress: String? = null,
    val details: String? = null,
    val createdAt: String
)

data class ConfiguredOnuHistoryDto(
    val items: List<ConfiguredOnuHistoryItemDto>
)

data class ConfiguredOnuPageDto(
    val items: List<ConfiguredOnuItemDto>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int
)

data class CatalogItemDto(
    val id: Long,
    val name: String
)

data class CatalogStringItemDto(
    val value: String,
    val label: String = value
)

data class BoardPortCatalogDto(
    val boards: List<Int>,
    val ports: List<Int>
)

data class OnuCatalogsDto(
    val olts: List<CatalogItemDto> = emptyList(),
    val zones: List<CatalogItemDto> = emptyList(),
    val onuTypes: List<CatalogItemDto> = emptyList(),
    val vlans: List<Int> = emptyList(),
    val profiles: List<CatalogStringItemDto> = emptyList(),
    val splitters: List<CatalogItemDto> = emptyList(),
    val ponTypes: List<CatalogStringItemDto> = emptyList()
)

data class OltSnmpTrapVarbindDto(
    val oid: String,
    val value: String
)

data class OltSnmpTrapEventDto(
    val receivedAt: String,
    val sourceHost: String? = null,
    val community: String? = null,
    val trapOid: String? = null,
    val trapLabel: String? = null,
    val varbinds: List<OltSnmpTrapVarbindDto> = emptyList()
)

data class OltSnmpTrapRecentDto(
    val enabled: Boolean,
    val listenPort: Int,
    val items: List<OltSnmpTrapEventDto>
)
