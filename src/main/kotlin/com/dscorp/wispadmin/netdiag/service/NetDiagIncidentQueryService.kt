package com.dscorp.wispadmin.netdiag.service

import com.dscorp.wispadmin.netdiag.domain.entity.NetDiagIncident
import com.dscorp.wispadmin.netdiag.domain.entity.NetDiagIncidentEvent
import com.dscorp.wispadmin.netdiag.domain.repository.NetDiagIncidentEventRepository
import com.dscorp.wispadmin.netdiag.domain.repository.NetDiagIncidentRepository
import com.dscorp.wispadmin.netdiag.dto.IncidentDetailDto
import com.dscorp.wispadmin.netdiag.dto.IncidentEventDto
import com.dscorp.wispadmin.netdiag.dto.IncidentSummaryDto
import com.dscorp.wispadmin.netdiag.dto.IncidentsSummaryDto
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
    private val maintenanceService: NetDiagMaintenanceService,
    private val summaryCache: NetDiagIncidentSummaryCache
) {

    @Transactional(readOnly = true)
    fun listIncidents(
        severity: String? = null,
        status: String? = null,
        targetId: Long? = null,
        dateFrom: String? = null,
        dateTo: String? = null
    ): List<IncidentSummaryDto> {
        val from = parseInstantBound(dateFrom, endOfDay = false)
        val to = parseInstantBound(dateTo, endOfDay = true)
        val statuses = if (status.isNullOrBlank()) {
            ACTIVE_STATUSES
        } else {
            listOf(status.trim().uppercase())
        }
        val normalizedSeverity = severity?.trim()?.takeIf { it.isNotEmpty() }?.uppercase()
        return incidentRepository.findForList(statuses, normalizedSeverity, targetId, from, to)
            .map { toSummary(it) }
    }

    @Transactional(readOnly = true)
    fun summarizeIncidents(): IncidentsSummaryDto = summaryCache.get { computeSummary() }

    private fun computeSummary(): IncidentsSummaryDto {
        var open = 0L
        var p0Open = 0L
        var pollStale = 0L
        incidentRepository.summarizeByStatuses(OPEN_STATUSES).forEach { row ->
            val severity = row.getOrNull(0) as? String
            val reasonCode = row.getOrNull(1) as? String
            val count = (row.getOrNull(2) as? Number)?.toLong() ?: 0L
            open += count
            if (severity.equals("P0", ignoreCase = true)) p0Open += count
            if (reasonCode.equals("POLL_STALE", ignoreCase = true)) pollStale += count
        }
        return IncidentsSummaryDto(
            openCount = open,
            p0OpenCount = p0Open,
            pollStaleCount = pollStale
        )
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
                    reasonCode = incident.reasonCode,
                    targetId = incident.target?.id,
                    createdAt = Instant.now()
                )
            )
            summaryCache.invalidate()
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
                reasonCode = incident.reasonCode,
                targetId = incident.target?.id,
                createdAt = Instant.now()
            )
        )
        summaryCache.invalidate()
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
                reasonCode = incident.reasonCode,
                targetId = incident.target?.id,
                createdAt = Instant.now()
            )
        )
        summaryCache.invalidate()
        return toDetail(incident)
    }

    private fun findIncident(id: Long): NetDiagIncident {
        return incidentRepository.findByIdWithTarget(id)
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

    companion object {
        private val ACTIVE_STATUSES = listOf("OPEN", "ACKNOWLEDGED", "SILENCED")
        private val OPEN_STATUSES = listOf("OPEN", "ACKNOWLEDGED")
    }
}
