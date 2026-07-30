package com.dscorp.wispadmin.netdiag.service

import com.dscorp.wispadmin.netdiag.domain.entity.NetDiagIncident
import com.dscorp.wispadmin.netdiag.domain.entity.NetDiagIncidentEvent
import com.dscorp.wispadmin.netdiag.domain.repository.NetDiagIncidentEventRepository
import com.dscorp.wispadmin.netdiag.domain.repository.NetDiagIncidentRepository
import com.dscorp.wispadmin.netdiag.dto.IncidentDetailDto
import com.dscorp.wispadmin.netdiag.dto.IncidentEventDto
import com.dscorp.wispadmin.netdiag.dto.IncidentSummaryDto
import com.dscorp.wispadmin.netdiag.exception.IncidentNotFoundException
import com.dscorp.wispadmin.netdiag.exception.NetDiagConflictException
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeParseException

@Service
@ConditionalOnProperty(prefix = "net.diag", name = ["enabled"], havingValue = "true")
class NetDiagIncidentQueryService(
    private val incidentRepository: NetDiagIncidentRepository,
    private val incidentEventRepository: NetDiagIncidentEventRepository,
    private val maintenanceService: NetDiagMaintenanceService
) {

    fun listIncidents(
        severity: String? = null,
        status: String? = null,
        targetId: Long? = null,
        dateFrom: String? = null,
        dateTo: String? = null
    ): List<IncidentSummaryDto> {
        val from = parseInstantBound(dateFrom, endOfDay = false)
        val to = parseInstantBound(dateTo, endOfDay = true)
        return incidentRepository.findAll()
            .asSequence()
            .filter { status.isNullOrBlank() || it.status.equals(status, ignoreCase = true) }
            .filter { severity.isNullOrBlank() || it.severity.equals(severity, ignoreCase = true) }
            .filter { targetId == null || it.target?.id == targetId }
            .filter { from == null || !it.openedAt.isBefore(from) }
            .filter { to == null || !it.openedAt.isAfter(to) }
            .sortedByDescending { it.openedAt }
            .map { toSummary(it) }
            .toList()
    }

    fun getIncident(id: Long): IncidentDetailDto {
        val incident = findIncident(id)
        return toDetail(incident)
    }

    @Transactional
    fun acknowledge(id: Long): IncidentDetailDto {
        val incident = findIncident(id)
        if (incident.status.equals("RESOLVED", ignoreCase = true)) {
            throw NetDiagConflictException("Cannot acknowledge a resolved incident")
        }
        if (!incident.status.equals("ACKNOWLEDGED", ignoreCase = true)) {
            incident.status = "ACKNOWLEDGED"
            incident.acknowledgedAt = Instant.now()
            incidentRepository.save(incident)
            incidentEventRepository.save(
                NetDiagIncidentEvent(
                    incident = incident,
                    type = "ACKNOWLEDGED",
                    payload = null,
                    createdAt = Instant.now()
                )
            )
        }
        return toDetail(incident)
    }

    @Transactional
    fun resolve(id: Long): IncidentDetailDto {
        val incident = findIncident(id)
        if (incident.status.equals("RESOLVED", ignoreCase = true)) {
            throw NetDiagConflictException("Incident already resolved")
        }
        incident.status = "RESOLVED"
        incident.resolvedAt = Instant.now()
        if (incident.acknowledgedAt == null) {
            incident.acknowledgedAt = incident.resolvedAt
        }
        incidentRepository.save(incident)
        incidentEventRepository.save(
            NetDiagIncidentEvent(
                incident = incident,
                type = "RESOLVED",
                payload = null,
                createdAt = Instant.now()
            )
        )
        return toDetail(incident)
    }

    @Transactional
    fun silence(id: Long, until: Instant?): IncidentDetailDto {
        val incident = findIncident(id)
        if (incident.status.equals("RESOLVED", ignoreCase = true)) {
            throw NetDiagConflictException("Cannot silence a resolved incident")
        }
        val effectiveUntil = until ?: Instant.now().plusSeconds(3600)
        incident.status = "SILENCED"
        incident.silencedUntil = effectiveUntil
        incidentRepository.save(incident)
        incidentEventRepository.save(
            NetDiagIncidentEvent(
                incident = incident,
                type = "SILENCED",
                payload = effectiveUntil.toString(),
                createdAt = Instant.now()
            )
        )
        return toDetail(incident)
    }

    private fun findIncident(id: Long): NetDiagIncident {
        return incidentRepository.findById(id)
            .orElseThrow { IncidentNotFoundException("Incident not found: $id") }
    }

    private fun toSummary(incident: NetDiagIncident): IncidentSummaryDto {
        return IncidentSummaryDto(
            id = requireNotNull(incident.id),
            targetId = incident.target?.id,
            targetName = incident.target?.name,
            dedupKey = incident.dedupKey,
            status = incident.status,
            severity = incident.severity,
            title = incident.title,
            reasonCode = incident.reasonCode,
            openedAt = incident.openedAt,
            lastNotifiedAt = incident.lastNotifiedAt,
            silencedUntil = incident.silencedUntil
        )
    }

    private fun toDetail(incident: NetDiagIncident): IncidentDetailDto {
        val id = requireNotNull(incident.id)
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
            id = id,
            targetId = incident.target?.id,
            targetName = incident.target?.name,
            dedupKey = incident.dedupKey,
            status = incident.status,
            severity = incident.severity,
            title = incident.title,
            reasonCode = incident.reasonCode,
            openedAt = incident.openedAt,
            acknowledgedAt = incident.acknowledgedAt,
            resolvedAt = incident.resolvedAt,
            lastNotifiedAt = incident.lastNotifiedAt,
            silencedUntil = incident.silencedUntil,
            events = events
        )
    }

    private fun parseInstantBound(raw: String?, endOfDay: Boolean): Instant? {
        if (raw.isNullOrBlank()) return null
        return try {
            Instant.parse(raw)
        } catch (_: DateTimeParseException) {
            try {
                val date = LocalDate.parse(raw)
                if (endOfDay) {
                    date.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC).minusMillis(1)
                } else {
                    date.atStartOfDay().toInstant(ZoneOffset.UTC)
                }
            } catch (_: DateTimeParseException) {
                null
            }
        }
    }
}
