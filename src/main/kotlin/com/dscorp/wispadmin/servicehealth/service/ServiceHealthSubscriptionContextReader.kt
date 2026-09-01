package com.dscorp.wispadmin.servicehealth.service

import com.dscorp.wispadmin.servicehealth.dto.ServiceContext
import com.dscorp.wispadmin.servicehealth.dto.SubscriberContext
import com.dscorp.wispadmin.servicehealth.dto.SubscriptionContext
import com.dscorp.wispadmin.wispadmin.data.model.Subscription
import com.dscorp.wispadmin.wispadmin.repository.ServiceHealthSubscriptionView
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ServiceHealthSubscriptionContextReader(private val subscriptions: SubscriptionRepository) {
    @Transactional(readOnly = true)
    fun read(subscriptionId: Int): SubscriptionContext {
        val subscription = subscriptions.findServiceHealthContextById(subscriptionId)
            ?: throw NoSuchElementException("Suscripción inexistente")
        return SubscriptionContext(
            subscriber = SubscriberContext(
                displayName = displayName(subscription),
                clientType = subscription.getClientType().name,
            ),
            serviceContext = ServiceContext(
                serviceStatus = subscription.getServiceStatus().name,
                planName = subscription.getPlanName()?.trim()?.takeIf(String::isNotEmpty),
                ip = subscription.getIp()?.trim()?.takeIf(String::isNotEmpty),
            ),
        )
    }

    private fun displayName(subscription: ServiceHealthSubscriptionView): String {
        val personalName = listOf(subscription.getFirstName(), subscription.getLastName())
            .mapNotNull { it?.trim()?.takeIf(String::isNotEmpty) }
            .joinToString(" ")
        val businessName = subscription.getBusinessName()?.trim()?.takeIf(String::isNotEmpty)
        return if (subscription.getClientType() == Subscription.ClientType.BUSINESS) {
            businessName ?: personalName.ifBlank { null }
        } else {
            personalName.ifBlank { null } ?: businessName
        } ?: "Cliente sin nombre registrado"
    }
}
