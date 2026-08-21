package com.dscorp.wispadmin.wispadmin.service.subscription.strategies

import com.dscorp.wispadmin.routeros.port.MikrotikException
import com.dscorp.wispadmin.routeros.port.MikrotikSession
import com.dscorp.wispadmin.wispadmin.data.model.NetworkDevice
import com.dscorp.wispadmin.wispadmin.data.model.Plan
import com.dscorp.wispadmin.wispadmin.data.model.ServiceStatus
import com.dscorp.wispadmin.wispadmin.data.model.Subscription
import com.dscorp.wispadmin.wispadmin.extensions.NetworkDeviceConnectionManager
import com.dscorp.wispadmin.wispadmin.extensions.executeCommand
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionRepository
import com.dscorp.wispadmin.wispadmin.service.mikrotik.IQueueManager
import com.dscorp.wispadmin.wispadmin.service.mikrotik.SimpleQueueNameParser
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

data class QueueEnsureResult(
    val added: Boolean,
    val error: String? = null
)

@Component
class SimpleQueueProvisioner(
    private val queueManager: IQueueManager,
    private val subscriptionRepository: SubscriptionRepository
) {
    private val logger = LoggerFactory.getLogger(SimpleQueueProvisioner::class.java)

    fun ensureQueue(subscription: Subscription, device: NetworkDevice, plan: Plan): QueueEnsureResult {
        if (NetworkDeviceConnectionManager.isMikroTikMockModeEnabled()) {
            return QueueEnsureResult(added = true)
        }

        val target = subscription.ip.orEmpty()
        var result = QueueEnsureResult(added = false)
        return try {
            device.executeCommand { session ->
                val existing = session.print("/queue/simple", mapOf("target" to "$target/32"))
                if (existing.isEmpty()) {
                    session.add(
                        "/queue/simple",
                        mapOf(
                            "name" to queueManager.buildQueueName(subscription),
                            "target" to target,
                            "max-limit" to "${plan.uploadSpeed}M/${plan.downloadSpeed}M"
                        )
                    )
                    result = QueueEnsureResult(added = true)
                    return@executeCommand
                }

                val queueName = existing.last()["name"]
                val ownerId = SimpleQueueNameParser.subscriptionId(queueName)
                result = when {
                    ownerId != null && ownerId == subscription.id -> QueueEnsureResult(added = true)
                    ownerId != null -> QueueEnsureResult(
                        added = false,
                        error = "La IP $target ya tiene queue de la suscripción $ownerId ($queueName)"
                    )
                    else -> reclaimOrphan(subscription, target, session)
                }
            }
            result
        } catch (error: MikrotikException) {
            logger.error(
                "No se pudo crear simple queue en MikroTik para suscripción ${subscription.id}",
                error
            )
            QueueEnsureResult(added = false, error = error.message)
        }
    }

    private fun reclaimOrphan(
        subscription: Subscription,
        target: String,
        session: MikrotikSession
    ): QueueEnsureResult {
        val otherActive = subscriptionRepository
            .findByIpAndServiceStatus(target, ServiceStatus.ACTIVE)
            .any { it.id != subscription.id }
        if (otherActive) {
            return QueueEnsureResult(
                added = false,
                error = "La IP $target tiene queue huérfana y otra suscripción ACTIVE"
            )
        }
        queueManager.recreateQueueForSubscription(session, subscription)
        return QueueEnsureResult(added = true)
    }
}
