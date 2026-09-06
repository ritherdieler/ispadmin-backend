package com.dscorp.wispadmin.wispadmin.service.subscription.strategies

import com.dscorp.wispadmin.wispadmin.config.GigafiberEnvironmentProperties
import com.dscorp.wispadmin.wispadmin.data.model.NetworkDevice
import com.dscorp.wispadmin.wispadmin.data.model.Plan
import com.dscorp.wispadmin.wispadmin.data.model.Place
import com.dscorp.wispadmin.wispadmin.data.model.Subscription
import com.dscorp.wispadmin.wispadmin.requestbody.SubscriptionRequest
import com.dscorp.wispadmin.wispadmin.requestbody.smartoltrequest.OnuAuthorizationRequest
import com.dscorp.wispadmin.wispadmin.oltclient.GatewayOnuActivateRequest
import com.dscorp.wispadmin.wispadmin.oltclient.GatewayOnuActivationClient
import com.dscorp.wispadmin.wispadmin.service.CancelledOnuReuseService
import com.dscorp.wispadmin.wispadmin.service.subscription.SubscriptionVlanRules
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Component

@Component
class FiberInstallationStrategy(
    private val cancelledOnuReuseService: CancelledOnuReuseService,
    private val simpleQueueProvisioner: SimpleQueueProvisioner,
    private val environment: GigafiberEnvironmentProperties,
    private val gatewayActivation: ObjectProvider<GatewayOnuActivationClient>,
) : IInstallationStrategy {

    private val logger = LoggerFactory.getLogger(FiberInstallationStrategy::class.java)

    companion object {
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
        var uniqueExternalId: String? = null
        var cpeStatus: String? = null
        var mikrotikError: String? = null
        var oltError: String? = null

        request.onu?.let { onuRequest ->
            onuSn = onuRequest.sn
            val vlan = resolveVlan(subscription)
            val gateway = gatewayActivation.ifAvailable
            if (gateway != null) {
                logger.info(
                    "FIBER OLT via Gateway activate sn={} subscriptionId={}",
                    onuRequest.sn,
                    subscription.id,
                )
                try {
                    val activated = gateway.activate(
                        GatewayOnuActivateRequest(
                            oltId = onuRequest.olt_id,
                            ponType = onuRequest.pon_type,
                            board = onuRequest.board,
                            port = onuRequest.port,
                            sn = onuRequest.sn,
                            vlan = vlan,
                            onuType = onuRequest.onu_type_name,
                            zone = DEFAULT_ZONE,
                            name = subscription.getFullName(),
                            onuMode = DEFAULT_ONU_MODE,
                            customProfile = DEFAULT_CUSTOM_PROFILE,
                            ip = subscription.ip,
                            ipSegment = subscription.ipPool?.ipSegment,
                            wifiSsid24 = request.wifiSsid24,
                            wifiPassword24 = request.wifiPassword24,
                            wifiSsid5 = request.wifiSsid5,
                            wifiPassword5 = request.wifiPassword5,
                        )
                    )
                    uniqueExternalId = activated.uniqueExternalId
                    cpeStatus = activated.cpeStatus
                    onuAuthorized = activated.oltStatus.equals("COMPLETE", ignoreCase = true)
                    if (!onuAuthorized) {
                        oltError = activated.message ?: "OLT activate failed"
                    }
                    logger.info(
                        "FIBER OLT Gateway result sn={} oltStatus={} cpeStatus={} uniqueExternalId={}",
                        onuRequest.sn,
                        activated.oltStatus,
                        activated.cpeStatus,
                        activated.uniqueExternalId,
                    )
                } catch (error: Exception) {
                    oltError = error.message ?: "Error autorizando ONU en Gateway"
                    logger.error(
                        "No se pudo activar ONU via Gateway para suscripción ${subscription.id}",
                        error
                    )
                }
            } else {
                logger.warn(
                    "FIBER OLT Gateway client unavailable; SmartOLT fallback sn={} subscriptionId={}",
                    onuRequest.sn,
                    subscription.id,
                )
                val authorizeRequest = OnuAuthorizationRequest(
                    olt_id = onuRequest.olt_id,
                    pon_type = onuRequest.pon_type,
                    board = onuRequest.board,
                    port = onuRequest.port,
                    sn = onuRequest.sn,
                    vlan = vlan,
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
            }

            val queueResult = simpleQueueProvisioner.ensureQueue(subscription, device, plan)
            queueAdded = queueResult.added
            mikrotikError = queueResult.error
        } ?: run {
            oltError = "Solicitud FIBER sin datos de ONU"
        }

        return InstallationResult(
            queueAdded = queueAdded,
            onuAuthorized = onuAuthorized,
            onuSn = onuSn,
            uniqueExternalId = uniqueExternalId,
            cpeStatus = cpeStatus,
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

    /**
     * VLAN for SmartOLT authorize_onu — must match the value sent from the app
     * and the GenieACS TR-069 WAN VLAN. No hostDevice / global fallback.
     */
    fun resolveVlan(subscription: Subscription): String {
        val hostDevice = subscription.hostDevice
            ?: throw IllegalStateException("La suscripción debe tener hostDevice asignado")

        if (hostDevice.disabled) {
            throw IllegalStateException(
                "network_device id=${hostDevice.id} esta deshabilitado"
            )
        }

        val vlan = SubscriptionVlanRules.requireAppVlan(subscription.vlan)
        SubscriptionVlanRules.assertPoolAligned(
            vlan = vlan,
            ipSegment = subscription.ipPool?.ipSegment,
            environmentTag = environment.normalizedTag(),
        )
        return vlan
    }
}
