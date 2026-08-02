package com.dscorp.wispadmin.wispadmin.adapter

import com.dscorp.wispadmin.netdiag.port.NetDiagOntSubscriptionPort
import com.dscorp.wispadmin.netdiag.port.OntSubscriptionInfo
import com.dscorp.wispadmin.wispadmin.data.model.Subscription
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionRepository
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(prefix = "net.diag", name = ["enabled"], havingValue = "true")
class WispAdminOntSubscriptionAdapter(
    private val subscriptionRepository: SubscriptionRepository
) : NetDiagOntSubscriptionPort {

    override fun findActiveByOnuSn(sn: String): OntSubscriptionInfo? {
        val suffix = if (sn.length >= SUFFIX_LENGTH) sn.takeLast(SUFFIX_LENGTH) else sn
        val matches = subscriptionRepository.findActiveByFiberOnuSn(sn, suffix)
        if (matches.isEmpty()) return null
        val subscription = matches.minByOrNull { it.id ?: Int.MAX_VALUE } ?: return null
        return toInfo(subscription)
    }

    private fun toInfo(subscription: Subscription): OntSubscriptionInfo {
        val customerName = when (subscription.clientType) {
            Subscription.ClientType.BUSINESS -> subscription.businessName?.takeIf { it.isNotBlank() }
            else -> subscription.getFullName().trim().takeIf { it.isNotBlank() }
        }
        return OntSubscriptionInfo(
            subscriptionId = subscription.id,
            customerName = customerName,
            serviceStatus = subscription.serviceStatus.name,
            napBoxCode = subscription.napBox?.code?.takeIf { it.isNotBlank() }
        )
    }

    companion object {
        private const val SUFFIX_LENGTH = 8
    }
}
