package com.dscorp.wispadmin.netdiag.service

import com.dscorp.wispadmin.netdiag.domain.entity.NetDiagMaintenanceWindow
import com.dscorp.wispadmin.netdiag.domain.repository.NetDiagMaintenanceWindowRepository
import com.dscorp.wispadmin.netdiag.dto.MaintenanceWindowDto
import com.dscorp.wispadmin.netdiag.exception.NetDiagConflictException
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
@ConditionalOnProperty(prefix = "net.diag", name = ["enabled"], havingValue = "true")
class NetDiagMaintenanceService(
    private val repository: NetDiagMaintenanceWindowRepository
) {

    fun list(): List<MaintenanceWindowDto> {
        return repository.findAll()
            .sortedByDescending { it.startsAt }
            .map { toDto(it) }
    }

    @Transactional
    fun create(
        targetId: Long?,
        title: String,
        description: String?,
        startsAt: Instant,
        endsAt: Instant
    ): MaintenanceWindowDto {
        if (!endsAt.isAfter(startsAt)) {
            throw NetDiagConflictException("endsAt must be after startsAt")
        }
        val saved = repository.save(
            NetDiagMaintenanceWindow(
                targetId = targetId,
                title = title.trim(),
                description = description?.trim()?.takeIf { it.isNotEmpty() },
                startsAt = startsAt,
                endsAt = endsAt,
                suppressNotifications = true,
                createdAt = Instant.now()
            )
        )
        return toDto(saved)
    }

    @Transactional
    fun delete(id: Long) {
        if (!repository.existsById(id)) {
            throw NetDiagConflictException("Maintenance window not found: $id")
        }
        repository.deleteById(id)
    }

    fun isNotificationsSuppressed(targetId: Long?, at: Instant = Instant.now()): Boolean {
        return repository.findActiveAt(at).any { window ->
            window.suppressNotifications && (window.targetId == null || window.targetId == targetId)
        }
    }

    fun isIncidentSilenced(silencedUntil: Instant?, at: Instant = Instant.now()): Boolean {
        return silencedUntil != null && at.isBefore(silencedUntil)
    }

    private fun toDto(window: NetDiagMaintenanceWindow): MaintenanceWindowDto {
        return MaintenanceWindowDto(
            id = requireNotNull(window.id),
            targetId = window.targetId,
            title = window.title,
            description = window.description,
            startsAt = window.startsAt,
            endsAt = window.endsAt,
            suppressNotifications = window.suppressNotifications,
            createdAt = window.createdAt
        )
    }
}
