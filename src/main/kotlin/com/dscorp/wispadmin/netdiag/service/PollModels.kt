package com.dscorp.wispadmin.netdiag.service

import com.dscorp.wispadmin.netdiag.domain.entity.NetDiagProbeRun

data class PollResult(
    val probeRun: NetDiagProbeRun,
    val status: String,
    val payload: String?,
    val errorReasonCode: String? = null,
    val snapshot: PollSnapshot? = null
)

data class PollSnapshot(
    val interfaces: List<InterfaceSnapshot>,
    val health: List<HealthSnapshot>,
    val routerboard: RouterboardSnapshot?,
    val resource: ResourceSnapshot?,
    val criticalInterfaces: List<String>,
    val expectedFirmware: String?,
    val previousUptimeSeconds: Long?,
    val netwatch: List<NetwatchSnapshot> = emptyList(),
    val optical: List<OpticalSnapshot> = emptyList()
)

data class InterfaceSnapshot(
    val name: String,
    val type: String,
    val running: Boolean,
    val disabled: Boolean
)

data class HealthSnapshot(
    val name: String,
    val value: String
)

data class RouterboardSnapshot(
    val currentFirmware: String?,
    val upgradeFirmware: String?
)

data class ResourceSnapshot(
    val uptimeRaw: String?,
    val uptimeSeconds: Long?,
    val cpuLoad: Int?,
    val version: String?
)

data class NetwatchSnapshot(
    val name: String,
    val host: String,
    val status: String,
    val type: String?,
    val since: String?,
    val comment: String?
)

data class OpticalSnapshot(
    val interfaceName: String,
    val rxPowerDbm: Double?,
    val txPowerDbm: Double?,
    val temperatureC: Double?,
    val sfpPresent: Boolean?
)

data class AlertSignal(
    val reasonCode: String,
    val severity: String,
    val title: String,
    val dedupKey: String,
    val details: String? = null
)

data class AlertEvaluationResult(
    val decisions: List<String>,
    val openedIncidentIds: List<Long>,
    val suppressed: Boolean = false
)

data class TargetMonitorConfig(
    val criticalInterfaces: List<String> = emptyList(),
    val expectedFirmware: String? = null,
    val cpuThreshold: Int? = null,
    val netwatchNames: List<String> = emptyList(),
    val opticalInterfaces: List<String> = emptyList()
)

data class SyslogClassification(
    val reasonCode: String,
    val severity: String,
    val title: String,
    val component: String,
    val details: String? = null
)

data class ParsedSnmpTrap(
    val sourceHost: String?,
    val trapType: String?,
    val oid: String?,
    val raw: String
)
