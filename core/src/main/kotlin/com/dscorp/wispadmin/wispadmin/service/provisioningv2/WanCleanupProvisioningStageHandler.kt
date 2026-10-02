package com.dscorp.wispadmin.wispadmin.service.provisioningv2

import com.dscorp.wispadmin.wispadmin.acsclient.AcsCpeCoreClient
import com.dscorp.wispadmin.wispadmin.acsclient.CoreOnboardingV2WanCleanupRequest
import com.fasterxml.jackson.databind.ObjectMapper

data class WanCleanupProvisioningResource(
    val taskId: String,
    val deviceId: String,
    val model: String,
    val firmware: String,
    val mode: String = "pppoe",
)

/** Block 3 removes WANs not tagged by this operation after Internet and Wi-Fi have completed. */
class WanCleanupProvisioningStageHandler(
    private val acs: AcsCpeCoreClient,
    private val json: ObjectMapper,
) : ProvisioningStageHandler {
    override val stage = ProvisioningStage.WAN_CLEANUP

    override fun reconcile(context: ProvisioningStageContext): StageObservation {
        val resource = context.resourceSnapshot(RESOURCE_KEY)?.let(::parse) ?: return StageObservation.NEEDS_APPLY
        return observed(resource, context.operation)
    }

    override fun apply(context: ProvisioningStageContext): StageObservation {
        val contact = parseContact(context.resourceSnapshot(CONTACT_RESOURCE_KEY)
            ?: fail("ACS_CONTACT_RESOURCE_MISSING", false))
        val registration = decode<ProvisioningV2RegistrationSnapshot>(
            context.resourceSnapshot(ProvisioningV2RegistrationService.REGISTRATION_RESOURCE_KEY)
                ?: fail("REGISTRATION_SNAPSHOT_MISSING", false),
            "REGISTRATION_SNAPSHOT_INVALID",
        )
        val mode = if (registration.accessMode == "STATIC_IP") "static" else "pppoe"
        context.assertLease()
        val queued = acs.enqueueOnboardingV2WanCleanup(CoreOnboardingV2WanCleanupRequest(
            operationId = context.operation.id,
            sn = context.operation.serial,
            deviceId = contact.deviceId,
            model = contact.model,
            firmware = contact.firmware,
            mode = mode,
        ))
        if (queued.taskId.isBlank()) fail("ACS_WAN_CLEANUP_TASK_UNCONFIRMED")
        val resource = WanCleanupProvisioningResource(queued.taskId, contact.deviceId, contact.model, contact.firmware, mode)
        context.captureResource(RESOURCE_KEY, json.writeValueAsString(resource))
        return observed(resource, context.operation)
    }

    /** Cleanup is intentionally not reversible: unknown WANs have no trusted ownership snapshot. */
    override fun compensate(context: ProvisioningStageContext): StageObservation = StageObservation.SATISFIED

    private fun observed(resource: WanCleanupProvisioningResource, operation: ProvisioningOperation): StageObservation {
        val response = acs.onboardingV2WanCleanupStatus(CoreOnboardingV2WanCleanupRequest(
            operation.id, operation.serial, resource.deviceId, resource.model, resource.firmware, resource.mode,
        ))
        return when (response.state) {
            "COMPLETE" -> StageObservation.SATISFIED
            "WAITING" -> StageObservation.WAITING
            "FAILED" -> fail("ACS_WAN_CLEANUP_TASK_FAILED", false, response.reason)
            else -> fail("ACS_WAN_CLEANUP_STATUS_INVALID")
        }
    }

    private fun parse(value: String): WanCleanupProvisioningResource = decode(value, "WAN_CLEANUP_RESOURCE_INVALID")
    private fun parseContact(value: String): AcsContactProvisioningResource = decode(value, "ACS_CONTACT_RESOURCE_INVALID")
    private inline fun <reified T> decode(value: String, code: String): T = try {
        json.readValue(value, T::class.java)
    } catch (_: Exception) {
        fail(code, false)
    }
    private fun fail(code: String, retryable: Boolean = true, reason: String? = null): Nothing = throw ProvisioningStepException(
        ProvisioningFailure(code, reason?.takeIf { it.isNotBlank() } ?: "No se pudo confirmar la limpieza de WAN. Consulte el historial.", retryable),
    )

    private companion object {
        const val RESOURCE_KEY = "wan-cleanup"
        const val CONTACT_RESOURCE_KEY = "acs-contact"
    }
}
