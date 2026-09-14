package com.dscorp.wispadmin.netdiag.adapter

import com.dscorp.wispadmin.netdiag.config.NetDiagProperties
import com.dscorp.wispadmin.netdiag.domain.entity.NetDiagIncident
import com.dscorp.wispadmin.netdiag.domain.entity.NetDiagIncidentEvent
import com.dscorp.wispadmin.netdiag.domain.entity.NetDiagTarget
import com.dscorp.wispadmin.netdiag.domain.repository.NetDiagIncidentEventRepository
import com.dscorp.wispadmin.netdiag.domain.repository.NetDiagIncidentRepository
import com.dscorp.wispadmin.netdiag.domain.repository.NetDiagMaintenanceWindowRepository
import com.dscorp.wispadmin.netdiag.domain.repository.NetDiagOltLogEventRepository
import com.dscorp.wispadmin.netdiag.domain.repository.NetDiagProbeRunRepository
import com.dscorp.wispadmin.netdiag.domain.repository.NetDiagTargetRepository
import com.dscorp.wispadmin.servicehealth.port.HealthMaintenanceWindow
import com.dscorp.wispadmin.servicehealth.port.HealthNetDiagIncident
import com.dscorp.wispadmin.servicehealth.port.HealthNetDiagPort
import com.dscorp.wispadmin.servicehealth.port.HealthNetDiagProbe
import com.dscorp.wispadmin.servicehealth.port.HealthNetDiagTarget
import com.dscorp.wispadmin.servicehealth.port.HealthOltLogEvent
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Component
import java.time.Instant

@Component
@ConditionalOnProperty(prefix = "net.diag", name = ["enabled"], havingValue = "true")
class HealthNetDiagAdapter(
    private val targets: NetDiagTargetRepository,
    private val probes: NetDiagProbeRunRepository,
    private val incidents: NetDiagIncidentRepository,
    private val incidentEvents: NetDiagIncidentEventRepository,
    private val maintenance: NetDiagMaintenanceWindowRepository,
    private val logs: NetDiagOltLogEventRepository,
    private val properties: NetDiagProperties
) : HealthNetDiagPort {

    override fun enabledTargets(): List<HealthNetDiagTarget> = targets.findByEnabledTrue().map { it.toDto() }

    override fun findTarget(id: Long): HealthNetDiagTarget? = targets.findById(id).orElse(null)?.toDto()

    override fun latestProbe(targetId: Long): HealthNetDiagProbe? {
        val probe = probes.findTopByTargetIdOrderByStartedAtDesc(targetId).orElse(null) ?: return null
        return HealthNetDiagProbe(id = probe.id, finishedAt = probe.finishedAt, status = probe.status, payload = probe.payload)
    }

    override fun incidentsByTargetAndStatus(targetId: Long, status: String): List<HealthNetDiagIncident> {
        return incidents.findByTarget_IdAndStatus(targetId, status).map { it.toDto() }
    }

    override fun findIncident(id: Long): HealthNetDiagIncident? = incidents.findById(id).orElse(null)?.toDto()

    override fun findClosedByDedup(dedupKey: String, status: String): HealthNetDiagIncident? {
        return incidents.findTopByDedupKeyAndStatusOrderByOpenedAtDesc(dedupKey, status)?.toDto()
    }

    override fun saveIncident(
        target: HealthNetDiagTarget,
        dedupKey: String,
        status: String,
        severity: String,
        title: String,
        reasonCode: String,
        openedAt: Instant
    ): HealthNetDiagIncident {
        val entity = targets.findById(target.id ?: -1).orElse(null)
        val saved = incidents.save(
            NetDiagIncident(
                target = entity,
                dedupKey = dedupKey,
                status = status,
                severity = severity,
                title = title,
                reasonCode = reasonCode,
                openedAt = openedAt
            )
        )
        return saved.toDto()
    }

    override fun updateIncident(incident: HealthNetDiagIncident): HealthNetDiagIncident {
        val entity = incidents.findById(incident.id ?: -1).orElse(null) ?: return incident
        entity.status = incident.status
        entity.resolvedAt = incident.resolvedAt
        return incidents.save(entity).toDto()
    }

    override fun saveIncidentEvent(incident: HealthNetDiagIncident, type: String, payload: String, createdAt: Instant) {
        val entity = incidents.findById(incident.id ?: -1).orElse(null) ?: return
        incidentEvents.save(NetDiagIncidentEvent(incident = entity, type = type, payload = payload, createdAt = createdAt))
    }

    override fun findActiveMaintenance(now: Instant): List<HealthMaintenanceWindow> {
        return maintenance.findActiveAt(now).map { HealthMaintenanceWindow(id = it.id, targetId = it.targetId) }
    }

    override fun findOltLogChanges(after: Instant, afterId: Long?, page: Pageable): List<HealthOltLogEvent> {
        return logs.findChanges(after, afterId ?: 0L, page).mapNotNull { event ->
            val id = event.id ?: return@mapNotNull null
            HealthOltLogEvent(
                id = id,
                receivedAt = event.receivedAt,
                reasonCode = event.reasonCode,
                board = event.board,
                port = event.port,
                onuIndex = event.onuIndex,
                targetId = event.targetId,
                isClear = event.isClear,
                isUnparsed = event.isUnparsed
            )
        }
    }

    override fun cpuThreshold(): Int = properties.alert.cpuThreshold

    private fun NetDiagTarget.toDto() = HealthNetDiagTarget(
        id = id,
        name = name,
        deviceRefId = deviceRefId,
        parentTargetId = parentTargetId,
        pollIntervalMs = pollIntervalMs,
        monitorConfig = monitorConfig
    )

    private fun NetDiagIncident.toDto() = HealthNetDiagIncident(
        id = id,
        targetId = target?.id,
        dedupKey = dedupKey,
        status = status,
        reasonCode = reasonCode,
        resolvedAt = resolvedAt
    )
}
