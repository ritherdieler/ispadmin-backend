package com.dscorp.wispadmin.wispadmin.service.provisioningv2

import com.dscorp.wispadmin.shared.config.GigafiberEnvironmentProperties
import com.dscorp.wispadmin.wispadmin.data.model.InstallationType
import com.dscorp.wispadmin.wispadmin.data.model.Subscription
import com.dscorp.wispadmin.wispadmin.requestbody.SubscriptionRequest
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Service

@ConfigurationProperties(prefix = "provisioning")
class ProvisioningV2Properties {
    var enabled: Boolean = false
    var workerEnabled: Boolean = false
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
    val internetVlan: Int,
    val wifiSsid24: String?,
    val wifiPassword24: String?,
    val wifiSsid5: String?,
    val wifiPassword5: String?,
    val accessMode: String = "PPPOE_DYNAMIC",
)

@Service
class ProvisioningV2RegistrationService(
    private val journal: ProvisioningJournal,
    private val resources: ProvisioningResourceStore,
    private val json: ObjectMapper,
    private val environment: GigafiberEnvironmentProperties,
    private val properties: ProvisioningV2Properties,
) {
    fun start(subscription: Subscription, request: SubscriptionRequest, operatorId: Long? = null): ProvisioningOperation {
        check(!request.registrationOperationId.isNullOrBlank()) { "PREAUTHORIZATION_REQUIRED" }
        return promotePreauthorization(
            subscription,
            request,
            requireNotNull(operatorId) { "AUTHENTICATED_OPERATOR_REQUIRED" },
        )
    }

    /** Checks ownership before idempotent subscription lookup and saves the final encrypted form before insertion. */
    fun prepareSubmission(operatorId: Long?, request: SubscriptionRequest) {
        val operationId = request.registrationOperationId?.trim()?.takeIf(String::isNotEmpty) ?: return
        check(properties.enabled) { "PROVISIONING_DISABLED" }
        val authenticatedOperator = requireNotNull(operatorId?.takeIf { it > 0 }) { "AUTHENTICATED_OPERATOR_REQUIRED" }
        val operation = journal.get(environmentTag(), operationId)
            ?.takeIf { it.operatorId == authenticatedOperator }
            ?: throw NoSuchElementException("OPERATION_NOT_FOUND")
        validatePreauthorizationTarget(operation, request)
        request.clientRequestId = operation.id
        if (operation.phase == ProvisioningPhase.READY_FOR_FORM && operation.state == ProvisioningState.READY_FOR_FORM &&
            operation.subscriptionId == null) {
            request.facadePhotoUrl = resources.preauthorizationResource(environmentTag(), operation.id, authenticatedOperator, PHOTO_RESOURCE_KEY)
            resources.savePreauthorizationResource(environmentTag(), operation.id, authenticatedOperator, DRAFT_RESOURCE_KEY,
                json.writeValueAsString(request))
        } else {
            require(operation.phase == ProvisioningPhase.PROVISIONING && operation.subscriptionId != null) {
                "ACS_CONFIRMATION_REQUIRED"
            }
        }
    }

    private fun promotePreauthorization(
        subscription: Subscription,
        request: SubscriptionRequest,
        operatorId: Long,
    ): ProvisioningOperation {
        check(properties.enabled) { "PROVISIONING_DISABLED" }
        require(subscription.installationType == InstallationType.FIBER) { "PROVISIONING_REQUIRES_FIBER" }
        val subscriptionId = requireNotNull(subscription.id) { "SUBSCRIPTION_ID_REQUIRED" }
        val operationId = requireNotNull(request.registrationOperationId?.trim()?.takeIf(String::isNotEmpty))
        val environmentTag = environmentTag()
        val before = journal.get(environmentTag, operationId)
            ?.takeIf { it.operatorId == operatorId && it.subscriptionId == null }
            ?: throw NoSuchElementException("OPERATION_NOT_FOUND")
        require(subscription.fiberOnuSn?.trim()?.uppercase() == before.serial) { "SUBSCRIPTION_ONU_SERIAL_MISMATCH" }
        validatePreauthorizationTarget(before, request)
        requireNotNull(before.oltEvidence) { "OLT_EVIDENCE_REQUIRED" }
        requireNotNull(before.acsContactEvidence) { "ACS_EVIDENCE_REQUIRED" }
        prepareSubmission(operatorId, request)
        val promoted = journal.promotePreauthorization(environmentTag, operationId, operatorId, subscriptionId)
        val olt = requireNotNull(promoted.oltEvidence) { "OLT_EVIDENCE_REQUIRED" }
        val contact = requireNotNull(promoted.acsContactEvidence) { "ACS_EVIDENCE_REQUIRED" }
        val snapshot = ProvisioningV2RegistrationSnapshot(
            onu = ProvisioningV2OnuSnapshot(
                requireNotNull(promoted.onuTarget).oltId,
                promoted.onuTarget.ponType,
                promoted.onuTarget.board,
                promoted.onuTarget.port,
                promoted.onuTarget.onuType,
            ),
            internetVlan = requireNotNull(request.vlan?.trim()?.toIntOrNull()) { "INTERNET_VLAN_REQUIRED" },
            wifiSsid24 = request.wifiSsid24,
            wifiPassword24 = request.wifiPassword24,
            wifiSsid5 = request.wifiSsid5,
            wifiPassword5 = request.wifiPassword5,
            accessMode = subscription.accessMode.name,
        )
        resources.captureInitial(promoted, REGISTRATION_RESOURCE_KEY, json.writeValueAsString(snapshot))
        resources.captureInitial(promoted, OLT_RESOURCE_KEY, json.writeValueAsString(olt))
        resources.captureInitial(promoted, ACS_CONTACT_RESOURCE_KEY, json.writeValueAsString(contact))
        return promoted
    }

    private fun validatePreauthorizationTarget(operation: ProvisioningOperation, request: SubscriptionRequest) {
        require(request.installationType == InstallationType.FIBER) { "PROVISIONING_REQUIRES_FIBER" }
        val serial = request.onu?.sn?.trim()?.uppercase()?.takeIf { it.matches(Regex("[A-Z0-9]{12,16}")) }
            ?: throw IllegalArgumentException("ONU_SERIAL_REQUIRED")
        require(serial == operation.serial) { "ONU_SERIAL_MISMATCH" }
        val target = requireNotNull(operation.onuTarget) { "ONU_TARGET_REQUIRED" }
        val onu = requireNotNull(request.onu) { "ONU_TARGET_REQUIRED" }
        require(onu.olt_id == target.oltId && onu.pon_type == target.ponType && onu.board == target.board &&
            onu.port == target.port && onu.onu_type_name == target.onuType) { "ONU_TARGET_MISMATCH" }
        require(request.vlan?.trim()?.toIntOrNull() == target.vlan) { "INTERNET_VLAN_MISMATCH" }
        require(target.vlan in 1..4094 && target.vlan != MANAGEMENT_VLAN) { "INTERNET_VLAN_REQUIRED" }
    }

    private fun environmentTag() = environment.normalizedTag().ifBlank { "prod" }

    companion object {
        const val REGISTRATION_RESOURCE_KEY = "registration"
        const val MANAGEMENT_VLAN = 1000
        const val DRAFT_RESOURCE_KEY = "registration-draft"
        const val PHOTO_RESOURCE_KEY = "registration-photo"
        const val OLT_RESOURCE_KEY = "olt"
        const val ACS_CONTACT_RESOURCE_KEY = "acs-contact"
    }
}
