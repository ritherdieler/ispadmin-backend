package com.dscorp.wispadmin.servicehealth.port

import org.springframework.data.domain.Pageable
import java.time.Instant
import java.time.LocalDateTime

data class HealthOnuRef(
    val id: Long,
    val sn: String,
    val externalId: String?,
    val oltId: Long?,
    val oltName: String?,
    val board: Int,
    val port: Int,
    val onuIndex: Int,
    val zoneId: Long? = null
)

interface HealthOnuPort {
    fun findBySn(sn: String): HealthOnuRef?
    fun findByExternalId(externalId: String): HealthOnuRef?
    fun findByOltBoardPortOnu(oltId: Long, board: Int, port: Int, onuIndex: Int): HealthOnuRef?
    fun findByOlt(oltId: Long): List<HealthOnuRef>
    fun findOltIdByName(name: String): Long?
}

data class HealthTrafficSample(
    val id: Long?,
    val hostDeviceId: Int,
    val collectedAt: LocalDateTime?,
    val sampleStatus: String,
    val avgMbpsDown: Double?,
    val avgMbpsUp: Double?,
    val queueId: String?
)

data class HealthTrafficRun(
    val id: Long?,
    val completedAt: LocalDateTime?,
    val status: String?
)

data class HealthTrafficAnomaly(
    val id: Long,
    val subscriptionId: Int?,
    val hostDeviceId: Int?,
    val eventStatus: String,
    val anomalyType: String,
    val lastEvaluatedAt: LocalDateTime,
    val coveragePct: Double,
    val confidence: Double,
    val evidenceJson: String?
)

interface HealthTrafficPort {
    fun latestSample(subscriptionId: Int): HealthTrafficSample?
    fun latestRun(hostDeviceId: Int): HealthTrafficRun?
    fun findAnomalyChanges(after: LocalDateTime, afterId: Long, page: Pageable): List<HealthTrafficAnomaly>
    fun minimumCoveragePct(): Double
}

data class HealthNetDiagTarget(
    val id: Long?,
    val name: String,
    val deviceRefId: Long,
    val parentTargetId: Long?,
    val pollIntervalMs: Long,
    val monitorConfig: String?
)

data class HealthNetDiagProbe(
    val id: Long?,
    val finishedAt: Instant?,
    val status: String?,
    val payload: String?
)

data class HealthNetDiagIncident(
    val id: Long?,
    val targetId: Long?,
    val dedupKey: String,
    val status: String,
    val reasonCode: String?,
    val resolvedAt: Instant? = null
)

data class HealthMaintenanceWindow(
    val id: Long?,
    val targetId: Long?
)

data class HealthOltLogEvent(
    val id: Long,
    val receivedAt: Instant,
    val reasonCode: String?,
    val board: Int?,
    val port: Int?,
    val onuIndex: Int?,
    val targetId: Long?,
    val isClear: Boolean,
    val isUnparsed: Boolean
)

interface HealthNetDiagPort {
    fun enabledTargets(): List<HealthNetDiagTarget>
    fun findTarget(id: Long): HealthNetDiagTarget?
    fun latestProbe(targetId: Long): HealthNetDiagProbe?
    fun incidentsByTargetAndStatus(targetId: Long, status: String): List<HealthNetDiagIncident>
    fun findIncident(id: Long): HealthNetDiagIncident?
    fun findClosedByDedup(dedupKey: String, status: String): HealthNetDiagIncident?
    fun saveIncident(
        target: HealthNetDiagTarget,
        dedupKey: String,
        status: String,
        severity: String,
        title: String,
        reasonCode: String,
        openedAt: Instant
    ): HealthNetDiagIncident
    fun updateIncident(incident: HealthNetDiagIncident): HealthNetDiagIncident
    fun saveIncidentEvent(incident: HealthNetDiagIncident, type: String, payload: String, createdAt: Instant)
    fun findActiveMaintenance(now: Instant): List<HealthMaintenanceWindow>
    fun findOltLogChanges(after: Instant, afterId: Long?, page: Pageable): List<HealthOltLogEvent>
    fun cpuThreshold(): Int
}

data class HealthOpticalRow(
    val slot: Int,
    val port: Int,
    val ontId: Int,
    val rxPowerDbm: Double?,
    val txPowerDbm: Double?,
    val oltRxPowerDbm: Double?,
    val temperatureC: Double?,
    val biasCurrentMa: Double?,
    val distanceM: Int?
)

data class HealthOpticalObservation(
    val oltId: Long,
    val observedAt: Instant,
    val rows: List<HealthOpticalRow>
)

interface HealthOltIngestPort {
    fun onOpticalFailure(oltId: Long, observedAt: Instant, reason: String)
    fun onOptical(observation: HealthOpticalObservation)
    fun onState(sn: String, state: String?, cause: String?, observedAt: Instant)
}

data class HealthLabOpticalRefresh(
    val collected: Boolean,
    val unmapped: Boolean = false,
    val error: String? = null
)

interface HealthLabOpticalPort {
    fun refreshSubscription(subscriptionId: Int): HealthLabOpticalRefresh
}

interface HealthLabScopePort {
    fun collects(subscriptionId: Int?): Boolean
    fun collectionSubscriptionIds(): Set<Int>
}
