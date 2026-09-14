package com.dscorp.wispadmin.netdiag.adapter

import com.dscorp.wispadmin.netdiag.domain.repository.NetDiagProbeRunRepository
import com.dscorp.wispadmin.netdiag.port.NetDiagRadiusImpactPort
import com.dscorp.wispadmin.netdiag.port.RadiusImpactSnapshot
import com.dscorp.wispadmin.wispadmin.data.model.ServiceStatus
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(prefix = "net.diag", name = ["enabled"], havingValue = "true")
class WispAdminRadiusImpactAdapter(
    private val subscriptionRepository: SubscriptionRepository,
    private val probeRunRepository: NetDiagProbeRunRepository,
    private val objectMapper: ObjectMapper
) : NetDiagRadiusImpactPort {

    override fun estimateImpact(targetId: Long?, deviceRefId: Long?): RadiusImpactSnapshot {
        val activeSubscriptions = subscriptionRepository.countByServiceStatus(ServiceStatus.ACTIVE)
        val pppActive = targetId?.let { readPppActiveCount(it) }
        val estimated = pppActive?.toLong()?.takeIf { it > 0 } ?: activeSubscriptions.takeIf { targetId == null }

        return RadiusImpactSnapshot(
            source = "wispadmin-subscriptions",
            activeSubscriptions = activeSubscriptions,
            pppActiveSessions = pppActive,
            estimatedAffected = estimated,
            notes = if (pppActive != null) {
                "PPP activas desde último probe_run del target"
            } else {
                "Total suscripciones activas ISP (sin probe PPP reciente)"
            }
        )
    }

    private fun readPppActiveCount(targetId: Long): Int? {
        val probe = probeRunRepository.findTopByTargetIdOrderByStartedAtDesc(targetId).orElse(null)
            ?: return null
        val payload = probe.payload ?: return null
        return try {
            val node = objectMapper.readTree(payload)
            node.path("pppActiveCount").takeIf { it.isInt || it.isIntegralNumber }?.asInt()
        } catch (_: Exception) {
            null
        }
    }
}
