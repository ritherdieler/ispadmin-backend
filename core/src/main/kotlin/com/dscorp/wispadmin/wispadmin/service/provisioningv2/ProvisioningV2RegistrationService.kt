package com.dscorp.wispadmin.wispadmin.service.provisioningv2

import com.dscorp.wispadmin.shared.config.GigafiberEnvironmentProperties
import com.dscorp.wispadmin.wispadmin.data.model.InstallationType
import com.dscorp.wispadmin.wispadmin.data.model.Subscription
import com.dscorp.wispadmin.wispadmin.requestbody.SubscriptionRequest
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Service
import java.util.UUID

@ConfigurationProperties(prefix = "provisioning")
class ProvisioningV2Properties {
    var enabled: Boolean = false
    var workerEnabled: Boolean = false
    var tr069ProfileId: Int = 0
}

data class ProvisioningV2OnuSnapshot(
    val oltId: String,
    val ponType: String,
    val board: String,
    val port: String,
    val onuType: String,
)

/** Encrypted at rest and never emitted through progress or observability. */
data class ProvisioningV2RegistrationSnapshot(
    val onu: ProvisioningV2OnuSnapshot,
    val tr069ProfileId: Int,
    val internetVlan: Int,
    val wifiSsid24: String?,
    val wifiPassword24: String?,
    val wifiSsid5: String?,
    val wifiPassword5: String?,
)

@Service
class ProvisioningV2RegistrationService(
    private val journal: ProvisioningJournal,
    private val resources: ProvisioningResourceStore,
    private val json: ObjectMapper,
    private val environment: GigafiberEnvironmentProperties,
    private val properties: ProvisioningV2Properties,
) {
    fun start(subscription: Subscription, request: SubscriptionRequest): ProvisioningOperation {
        check(properties.enabled) { "PROVISIONING_DISABLED" }
        require(properties.tr069ProfileId in 1..65535) { "PROVISIONING_TR069_PROFILE_REQUIRED" }
        require(subscription.installationType == InstallationType.FIBER) { "PROVISIONING_REQUIRES_FIBER" }
        val subscriptionId = requireNotNull(subscription.id) { "SUBSCRIPTION_ID_REQUIRED" }
        val serial = subscription.fiberOnuSn?.trim()?.uppercase()?.takeIf { it.matches(Regex("[A-Z0-9]{12,16}")) }
            ?: throw IllegalArgumentException("ONU_SERIAL_REQUIRED")
        val onu = requireNotNull(request.onu) { "ONU_TARGET_REQUIRED" }
        require(onu.sn.trim().equals(serial, ignoreCase = true)) { "ONU_SERIAL_MISMATCH" }
        require(onu.board.isNotBlank() && onu.port.isNotBlank() && onu.onu_type_name.isNotBlank()) { "ONU_TARGET_INCOMPLETE" }
        val internetVlan = request.vlan?.trim()?.toIntOrNull()
            ?.takeIf { it in 1..4094 && it != MANAGEMENT_VLAN }
            ?: throw IllegalArgumentException("INTERNET_VLAN_REQUIRED")
        val operation = ProvisioningOperation(
            id = UUID.randomUUID().toString(),
            environment = environment.normalizedTag().ifBlank { "prod" },
            subscriptionId = subscriptionId,
            serial = serial,
        )
        val snapshot = ProvisioningV2RegistrationSnapshot(
            onu = ProvisioningV2OnuSnapshot(onu.olt_id, onu.pon_type, onu.board, onu.port, onu.onu_type_name),
            tr069ProfileId = properties.tr069ProfileId,
            internetVlan = internetVlan,
            wifiSsid24 = request.wifiSsid24,
            wifiPassword24 = request.wifiPassword24,
            wifiSsid5 = request.wifiSsid5,
            wifiPassword5 = request.wifiPassword5,
        )
        journal.insert(operation)
        resources.captureInitial(operation, REGISTRATION_RESOURCE_KEY, json.writeValueAsString(snapshot))
        return operation
    }

    companion object {
        const val REGISTRATION_RESOURCE_KEY = "registration"
        const val MANAGEMENT_VLAN = 1000
    }
}
