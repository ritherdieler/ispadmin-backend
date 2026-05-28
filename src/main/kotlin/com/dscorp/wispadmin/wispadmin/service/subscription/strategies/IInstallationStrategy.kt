package com.dscorp.wispadmin.wispadmin.service.subscription.strategies

import com.dscorp.wispadmin.wispadmin.data.model.NetworkDevice
import com.dscorp.wispadmin.wispadmin.data.model.Plan
import com.dscorp.wispadmin.wispadmin.data.model.Place
import com.dscorp.wispadmin.wispadmin.data.model.Subscription
import com.dscorp.wispadmin.wispadmin.requestbody.SubscriptionRequest

interface IInstallationStrategy {
    
    fun processInstallation(
        subscription: Subscription,
        request: SubscriptionRequest,
        device: NetworkDevice,
        plan: Plan,
        place: Place
    ): InstallationResult
    
    fun buildQueueName(subscription: Subscription): String
}

data class InstallationResult(
    val queueAdded: Boolean,
    val onuAuthorized: Boolean = false,
    val onuSn: String? = null
)



