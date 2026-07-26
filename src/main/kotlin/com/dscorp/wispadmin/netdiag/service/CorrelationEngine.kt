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

    private val suppressedByUpstream = setOf(
        "LINK_DOWN",
        "GRE_TUNNEL_DOWN",
        "OPTICAL_RX_LOW",
        "OPTICAL_TX_FAULT",
        "LINK_FLAP",
        "SNMP_TRAP_LINK_DOWN"
    )

    fun findSuppressingAncestorIncident(targetId: Long): NetDiagIncident? {
        return findSuppressingAncestorIncident(targetId, null)
    }

    fun findSuppressingAncestorIncident(targetId: Long, reasonCode: String?): NetDiagIncident? {
        val sameTargetUpstream = findOpenByReason(targetId, "UPSTREAM_PROBE_FAIL")
        if (sameTargetUpstream != null && reasonCode != null && suppressedByUpstream.contains(reasonCode)) {
            return sameTargetUpstream
        }
        var currentParentId = targetRepository.findById(targetId).orElse(null)?.parentTargetId
        var depth = 0
        val maxDepth = properties.alert.parentMaxDepth.coerceAtLeast(1)
        while (currentParentId != null && depth < maxDepth) {
            val parentUpstream = findOpenByReason(currentParentId, "UPSTREAM_PROBE_FAIL")
            if (parentUpstream != null && reasonCode != null && suppressedByUpstream.contains(reasonCode)) {
                return parentUpstream
            }
            if (incidentRepository.existsByTarget_IdAndStatus(currentParentId, "OPEN")) {
                return incidentRepository.findByTarget_IdAndStatus(currentParentId, "OPEN").firstOrNull()
            }
            currentParentId = targetRepository.findById(currentParentId).orElse(null)?.parentTargetId
            depth++
        }
        return null
    }

    private fun findOpenByReason(targetId: Long, reasonCode: String): NetDiagIncident? {
        return incidentRepository.findByTarget_IdAndStatus(targetId, "OPEN")
            .firstOrNull { it.reasonCode == reasonCode }
    }
}
