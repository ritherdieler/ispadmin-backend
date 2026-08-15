package com.dscorp.wispadmin.wispadmin.service.subscription.strategies

import com.dscorp.wispadmin.routeros.port.MikrotikException
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
        val target = subscription.ip.orEmpty()
        return try {
            device.executeCommand { session ->
                val existing = session.print("/queue/simple", mapOf("target" to "$target/32"))
                if (existing.isNotEmpty()) {
                    return@executeCommand
                }
                session.add(
                    "/queue/simple",
                    mapOf(
                        "name" to queueName,
                        "target" to target,
                        "max-limit" to "${plan.uploadSpeed}M/${plan.downloadSpeed}M"
                    )
                )
            }
            InstallationResult(queueAdded = true)
        } catch (error: MikrotikException) {
            logger.error("No se pudo crear simple queue en MikroTik para suscripción ${subscription.id}", error)
            InstallationResult(queueAdded = false, mikrotikError = error.message)
        }
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
