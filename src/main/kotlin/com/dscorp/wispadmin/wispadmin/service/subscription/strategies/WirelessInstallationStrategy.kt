package com.dscorp.wispadmin.wispadmin.service.subscription.strategies

import com.dscorp.wispadmin.wispadmin.data.model.NetworkDevice
import com.dscorp.wispadmin.wispadmin.data.model.Plan
import com.dscorp.wispadmin.wispadmin.data.model.Place
import com.dscorp.wispadmin.wispadmin.data.model.Subscription
import com.dscorp.wispadmin.wispadmin.extensions.executeCommand
import com.dscorp.wispadmin.wispadmin.requestbody.SubscriptionRequest
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class WirelessInstallationStrategy : IInstallationStrategy {
    
    private val logger = LoggerFactory.getLogger(WirelessInstallationStrategy::class.java)
    
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
        val queueName = buildQueueName(subscription)
        
        device.executeCommand { connection ->
            val queueCommand =
                "/queue/simple/add name='$queueName' target=${subscription.ip} max-limit=${plan.uploadSpeed}M/${plan.downloadSpeed}M"
            connection.execute(queueCommand)
        }
        
        return InstallationResult(queueAdded = true)
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



