package com.dscorp.wispadmin.wispadmin.service.subscription.strategies

import com.dscorp.wispadmin.wispadmin.data.model.NetworkDevice
import com.dscorp.wispadmin.wispadmin.data.model.Plan
import com.dscorp.wispadmin.wispadmin.data.model.Place
import com.dscorp.wispadmin.wispadmin.data.model.Subscription
import com.dscorp.wispadmin.wispadmin.extensions.executeCommand
import com.dscorp.wispadmin.wispadmin.requestbody.SubscriptionRequest
import com.dscorp.wispadmin.wispadmin.requestbody.smartoltrequest.OnuAuthorizationRequest
import com.dscorp.wispadmin.wispadmin.service.OnuService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class FiberInstallationStrategy(
    private val onuService: OnuService
) : IInstallationStrategy {
    
    private val logger = LoggerFactory.getLogger(FiberInstallationStrategy::class.java)
    
    companion object {
        private const val DEFAULT_VLAN = "1"
        private const val DEFAULT_ZONE = "Zone 1"
        private const val DEFAULT_ONU_MODE = "Routing"
        private const val DEFAULT_CUSTOM_PROFILE = "Generic_1"
        private const val QUEUE_NAME_TEMPLATE = "id:%d, usuario:%s %s, lugar:%s, nap:%s, plan:%s, tipo:%s"
    }
    
    override fun processInstallation(
        subscription: Subscription,
        request: SubscriptionRequest,
        device: NetworkDevice,
        plan: Plan,
        place: Place
    ): InstallationResult {
        var queueAdded = false
        var onuAuthorized = false
        var onuSn: String? = null

        request.onu?.let { onuRequest ->
            onuSn = onuRequest.sn

            val authorizeRequest = OnuAuthorizationRequest(
                olt_id = onuRequest.olt_id,
                pon_type = onuRequest.pon_type,
                board = onuRequest.board,
                port = onuRequest.port,
                sn = onuRequest.sn,
                vlan = DEFAULT_VLAN,
                onu_type = onuRequest.onu_type_name,
                zone = DEFAULT_ZONE,
                name = subscription.getFullName(),
                onu_mode = DEFAULT_ONU_MODE,
                custom_profile = DEFAULT_CUSTOM_PROFILE
            )
            
            onuService.authorizeOnuInSmartOltWidthPostMethod(authorizeRequest)
            onuAuthorized = true

            val queueName = buildQueueName(subscription)
            device.executeCommand { connection ->
                val queueCommand =
                    "/queue/simple/add name='$queueName' target=${subscription.ip} max-limit=${plan.uploadSpeed}M/${plan.downloadSpeed}M"
                connection.execute(queueCommand)
            }
            queueAdded = true
        }

        return InstallationResult(
            queueAdded = queueAdded,
            onuAuthorized = onuAuthorized,
            onuSn = onuSn
        )
    }
    
    override fun buildQueueName(subscription: Subscription): String {
        val napBoxCode = subscription.napBox?.code ?: ""
        return QUEUE_NAME_TEMPLATE.format(
            subscription.id ?: 0,
            subscription.firstName ?: "",
            subscription.lastName ?: "",
            subscription.place?.name ?: "",
            napBoxCode,
            subscription.plan?.name ?: "",
            subscription.plan?.type ?: ""
        )
    }
}



