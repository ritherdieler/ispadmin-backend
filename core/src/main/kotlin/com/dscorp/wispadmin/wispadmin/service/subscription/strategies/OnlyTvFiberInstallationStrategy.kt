package com.dscorp.wispadmin.wispadmin.service.subscription.strategies

import com.dscorp.wispadmin.wispadmin.data.model.NetworkDevice
import com.dscorp.wispadmin.wispadmin.data.model.Plan
import com.dscorp.wispadmin.wispadmin.data.model.Place
import com.dscorp.wispadmin.wispadmin.data.model.Subscription
import com.dscorp.wispadmin.wispadmin.requestbody.SubscriptionRequest
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class OnlyTvFiberInstallationStrategy : IInstallationStrategy {
    
    private val logger = LoggerFactory.getLogger(OnlyTvFiberInstallationStrategy::class.java)
    
    override fun processInstallation(
        subscription: Subscription,
        request: SubscriptionRequest,
        device: NetworkDevice,
        plan: Plan,
        place: Place
    ): InstallationResult {
        logger.info("Procesando instalación de TV cable para suscripción ${subscription.id}")
        return InstallationResult(queueAdded = false)
    }
    
    override fun buildQueueName(subscription: Subscription): String {
        return "TV-${subscription.id}-${subscription.firstName}-${subscription.lastName}"
    }
}



