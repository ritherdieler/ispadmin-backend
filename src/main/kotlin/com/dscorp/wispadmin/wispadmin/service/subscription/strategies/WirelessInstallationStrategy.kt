package com.dscorp.wispadmin.wispadmin.service.subscription.strategies

import com.dscorp.wispadmin.wispadmin.data.model.NetworkDevice
import com.dscorp.wispadmin.wispadmin.data.model.Plan
import com.dscorp.wispadmin.wispadmin.data.model.Place
import com.dscorp.wispadmin.wispadmin.data.model.Subscription
import com.dscorp.wispadmin.wispadmin.requestbody.SubscriptionRequest
import org.springframework.stereotype.Component

@Component
class WirelessInstallationStrategy(
    private val simpleQueueProvisioner: SimpleQueueProvisioner
) : IInstallationStrategy {

    companion object {
        private const val QUEUE_NAME_TEMPLATE = "id:%d, usuario:%s %s, lugar:%s, plan:%s, tipo:%s"
    }

    override fun processInstallation(
        subscription: Subscription,
        request: SubscriptionRequest,
        device: NetworkDevice,
        plan: Plan,
        place: Place
    ): InstallationResult {
        val queueResult = simpleQueueProvisioner.ensureQueue(subscription, device, plan)
        return InstallationResult(
            queueAdded = queueResult.added,
            mikrotikError = queueResult.error
        )
    }

    override fun buildQueueName(subscription: Subscription): String {
        return QUEUE_NAME_TEMPLATE.format(
            subscription.id ?: 0,
            subscription.firstName ?: "",
            subscription.lastName ?: "",
            subscription.place?.name ?: "",
            subscription.plan?.name ?: "",
            subscription.plan?.type ?: ""
        )
    }
}
