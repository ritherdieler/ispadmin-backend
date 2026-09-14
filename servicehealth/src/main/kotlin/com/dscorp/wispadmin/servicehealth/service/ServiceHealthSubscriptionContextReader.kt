package com.dscorp.wispadmin.servicehealth.service

import com.dscorp.wispadmin.servicehealth.dto.ServiceContext
import com.dscorp.wispadmin.servicehealth.dto.SubscriberContext
import com.dscorp.wispadmin.servicehealth.dto.SubscriptionContext
import com.dscorp.wispadmin.servicehealth.port.SubscriptionDirectoryPort
import com.dscorp.wispadmin.servicehealth.port.SubscriptionHealthContext
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ServiceHealthSubscriptionContextReader(private val directory: SubscriptionDirectoryPort) {
    @Transactional(readOnly = true)
    fun read(subscriptionId: Int): SubscriptionContext {
        val subscription = directory.findContext(subscriptionId)
            ?: throw NoSuchElementException("Suscripción inexistente")
        return SubscriptionContext(
            subscriber = SubscriberContext(
                displayName = displayName(subscription),
                clientType = subscription.clientType,
            ),
            serviceContext = ServiceContext(
                serviceStatus = subscription.serviceStatus,
                planName = subscription.planName?.trim()?.takeIf(String::isNotEmpty),
                ip = subscription.ip?.trim()?.takeIf(String::isNotEmpty),
            ),
        )
    }

    private fun displayName(subscription: SubscriptionHealthContext): String {
        val personalName = listOf(subscription.firstName, subscription.lastName)
            .mapNotNull { it?.trim()?.takeIf(String::isNotEmpty) }
            .joinToString(" ")
        val businessName = subscription.businessName?.trim()?.takeIf(String::isNotEmpty)
        return if (subscription.clientType == "BUSINESS") {
            businessName ?: personalName.ifBlank { null }
        } else {
            personalName.ifBlank { null } ?: businessName
        } ?: "Cliente sin nombre registrado"
    }
}
