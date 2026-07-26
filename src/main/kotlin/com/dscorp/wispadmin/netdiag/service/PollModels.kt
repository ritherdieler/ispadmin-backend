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
    val previousUptimeSeconds: Long?
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
    val cpuThreshold: Int? = null
)
