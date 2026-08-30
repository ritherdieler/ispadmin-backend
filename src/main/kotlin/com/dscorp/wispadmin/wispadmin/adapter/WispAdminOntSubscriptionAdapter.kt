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
        // Ambiguous identity must never disclose an arbitrary subscriber in NOC/LLM context.
        val matches = subscriptionRepository.findByExactOnuSerial(sn).filter { it.serviceStatus.name == "ACTIVE" }
        val subscription = matches.singleOrNull() ?: return null
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

}
