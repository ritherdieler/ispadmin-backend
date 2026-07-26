package com.dscorp.wispadmin.netdiag.service

import com.dscorp.wispadmin.netdiag.config.NetDiagProperties
import com.dscorp.wispadmin.netdiag.domain.entity.NetDiagIncident
import com.dscorp.wispadmin.netdiag.domain.repository.NetDiagIncidentRepository
import com.dscorp.wispadmin.netdiag.domain.repository.NetDiagTargetRepository
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(prefix = "net.diag", name = ["enabled"], havingValue = "true")
class CorrelationEngine(
    private val incidentRepository: NetDiagIncidentRepository,
    private val targetRepository: NetDiagTargetRepository,
    private val properties: NetDiagProperties
) {

    fun findSuppressingAncestorIncident(targetId: Long): NetDiagIncident? {
        var currentParentId = targetRepository.findById(targetId).orElse(null)?.parentTargetId
        var depth = 0
        val maxDepth = properties.alert.parentMaxDepth.coerceAtLeast(1)
        while (currentParentId != null && depth < maxDepth) {
            if (incidentRepository.existsByTarget_IdAndStatus(currentParentId, "OPEN")) {
                return incidentRepository.findByTarget_IdAndStatus(currentParentId, "OPEN").firstOrNull()
            }
            currentParentId = targetRepository.findById(currentParentId).orElse(null)?.parentTargetId
            depth++
        }
        return null
    }
}
