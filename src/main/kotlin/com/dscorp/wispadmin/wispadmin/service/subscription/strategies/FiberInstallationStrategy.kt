package com.dscorp.wispadmin.wispadmin.service.subscription.strategies

import com.dscorp.wispadmin.routeros.port.MikrotikException
import com.dscorp.wispadmin.wispadmin.data.model.NetworkDevice
import com.dscorp.wispadmin.wispadmin.data.model.Plan
import com.dscorp.wispadmin.wispadmin.data.model.Place
import com.dscorp.wispadmin.wispadmin.data.model.Subscription
import com.dscorp.wispadmin.wispadmin.extensions.executeCommand
import com.dscorp.wispadmin.wispadmin.requestbody.SubscriptionRequest
import com.dscorp.wispadmin.wispadmin.requestbody.smartoltrequest.OnuAuthorizationRequest
import com.dscorp.wispadmin.wispadmin.service.CancelledOnuReuseService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class FiberInstallationStrategy(
    private val cancelledOnuReuseService: CancelledOnuReuseService
) : IInstallationStrategy {

    private val logger = LoggerFactory.getLogger(FiberInstallationStrategy::class.java)

    companion object {
        private const val FALLBACK_VLAN = "1"
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
        var mikrotikError: String? = null
        var oltError: String? = null

        request.onu?.let { onuRequest ->
            onuSn = onuRequest.sn

            val authorizeRequest = OnuAuthorizationRequest(
                olt_id = onuRequest.olt_id,
                pon_type = onuRequest.pon_type,
                board = onuRequest.board,
                port = onuRequest.port,
                sn = onuRequest.sn,
                vlan = resolveVlan(subscription),
                onu_type = onuRequest.onu_type_name,
                zone = DEFAULT_ZONE,
                name = subscription.getFullName(),
                onu_mode = DEFAULT_ONU_MODE,
                custom_profile = DEFAULT_CUSTOM_PROFILE
            )

            try {
                cancelledOnuReuseService.authorizeWithCancelledReuse(authorizeRequest)
                onuAuthorized = true
            } catch (error: Exception) {
                oltError = error.message ?: "Error autorizando ONU en OLT"
                logger.error(
                    "No se pudo autorizar ONU en OLT para suscripción ${subscription.id}",
                    error
                )
            }

            val queueName = buildQueueName(subscription)
            val target = subscription.ip.orEmpty()
            try {
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
                queueAdded = true
            } catch (error: MikrotikException) {
                mikrotikError = error.message
                logger.error(
                    "No se pudo crear simple queue en MikroTik para suscripción ${subscription.id}",
                    error
                )
            }
        } ?: run {
            oltError = "Solicitud FIBER sin datos de ONU"
        }

        return InstallationResult(
            queueAdded = queueAdded,
            onuAuthorized = onuAuthorized,
            onuSn = onuSn,
            mikrotikError = mikrotikError,
            oltError = oltError
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

    internal fun resolveVlan(subscription: Subscription): String {
        val hostDevice = subscription.hostDevice
            ?: throw IllegalStateException("La suscripción debe tener hostDevice asignado")

        if (hostDevice.disabled) {
            throw IllegalStateException(
                "network_device id=${hostDevice.id} esta deshabilitado"
            )
        }

        subscription.vlan?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }

        hostDevice.vlanId?.let { return it.toString() }

        if (hostDevice.networkDeviceType == NetworkDevice.NetworkDeviceType.CLOUD_CORE_ROUTER) {
            throw IllegalStateException(
                "CLOUD_CORE_ROUTER id=${hostDevice.id} debe tener vlanId configurado"
            )
        }

        return FALLBACK_VLAN
    }
}
