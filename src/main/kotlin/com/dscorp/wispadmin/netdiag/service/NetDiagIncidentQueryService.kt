package com.dscorp.wispadmin.netdiag.service

import com.dscorp.wispadmin.netdiag.domain.repository.NetDiagIncidentEventRepository
import com.dscorp.wispadmin.netdiag.domain.repository.NetDiagIncidentRepository
import com.dscorp.wispadmin.netdiag.dto.IncidentDetailDto
import com.dscorp.wispadmin.netdiag.dto.IncidentEventDto
import com.dscorp.wispadmin.netdiag.dto.IncidentSummaryDto
import com.dscorp.wispadmin.netdiag.exception.IncidentNotFoundException
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service

@Service
@ConditionalOnProperty(prefix = "net.diag", name = ["enabled"], havingValue = "true")
class NetDiagIncidentQueryService(
    private val incidentRepository: NetDiagIncidentRepository,
    private val incidentEventRepository: NetDiagIncidentEventRepository
) {

    fun listIncidents(): List<IncidentSummaryDto> {
        return incidentRepository.findAll()
            .sortedByDescending { it.openedAt }
            .map { incident ->
                IncidentSummaryDto(
                    id = requireNotNull(incident.id),
                    targetId = incident.target?.id,
                    dedupKey = incident.dedupKey,
                    status = incident.status,
                    severity = incident.severity,
                    title = incident.title,
                    reasonCode = incident.reasonCode,
                    openedAt = incident.openedAt
                )
            }
    }

    fun getIncident(id: Long): IncidentDetailDto {
        val incident = incidentRepository.findById(id)
            .orElseThrow { IncidentNotFoundException("Incident not found: $id") }
        val events = incidentEventRepository.findByIncidentIdOrderByCreatedAtDesc(id)
            .map { event ->
                IncidentEventDto(
                    id = requireNotNull(event.id),
                    type = event.type,
                    payload = event.payload,
                    createdAt = event.createdAt
                )
            }
        return IncidentDetailDto(
            id = requireNotNull(incident.id),
            targetId = incident.target?.id,
            dedupKey = incident.dedupKey,
            status = incident.status,
            severity = incident.severity,
            title = incident.title,
            reasonCode = incident.reasonCode,
            openedAt = incident.openedAt,
            acknowledgedAt = incident.acknowledgedAt,
            resolvedAt = incident.resolvedAt,
            events = events
        )
    }
}
