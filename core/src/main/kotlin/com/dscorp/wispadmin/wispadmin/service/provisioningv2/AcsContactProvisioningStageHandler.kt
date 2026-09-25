package com.dscorp.wispadmin.wispadmin.service.provisioningv2

import com.dscorp.wispadmin.wispadmin.acsclient.AcsCpeCoreClient
import com.dscorp.wispadmin.wispadmin.acsclient.CoreOnboardingV2ContactRequest
import com.dscorp.wispadmin.wispadmin.acsclient.CoreOnboardingV2ContactResponse
import com.dscorp.wispadmin.wispadmin.acsclient.CoreOnboardingV2ContactState
import com.fasterxml.jackson.databind.ObjectMapper

data class AcsContactProvisioningResource(
    val deviceId: String,
    val model: String,
    val firmware: String,
)

/** Waits for a unique, supported Inform; it never queues or mutates a CPE. */
class AcsContactProvisioningStageHandler(
    private val acs: AcsCpeCoreClient,
    private val json: ObjectMapper,
) : ProvisioningStageHandler {
    override val stage = ProvisioningStage.ACS_CONTACT

    override fun reconcile(context: ProvisioningStageContext): StageObservation {
        val stored = context.resourceSnapshot(RESOURCE_KEY) ?: return StageObservation.NEEDS_APPLY
        val expected = parse(stored)
        return when (val contact = contact(context)) {
            is Contact.Waiting -> StageObservation.WAITING
            is Contact.Ready -> if (contact.resource == expected) StageObservation.SATISFIED
            else failure("ACS_CONTACT_IDENTITY_CHANGED", retryable = false)
        }
    }

    override fun apply(context: ProvisioningStageContext): StageObservation = when (val contact = contact(context)) {
        is Contact.Waiting -> StageObservation.WAITING
        is Contact.Ready -> {
            context.assertLease()
            context.captureResource(RESOURCE_KEY, json.writeValueAsString(contact.resource))
            StageObservation.SATISFIED
        }
    }

    override fun compensate(context: ProvisioningStageContext): StageObservation = StageObservation.SATISFIED

    private fun contact(context: ProvisioningStageContext): Contact {
        context.assertLease()
        val response = acs.onboardingV2Contact(CoreOnboardingV2ContactRequest(context.operation.id, context.operation.serial))
        if (response.state == CoreOnboardingV2ContactState.WAITING) return Contact.Waiting
        return Contact.Ready(response.toResource())
    }

    private fun CoreOnboardingV2ContactResponse.toResource(): AcsContactProvisioningResource {
        val id = deviceId?.takeIf { it.isNotBlank() } ?: failure("ACS_CONTACT_DEVICE_MISSING")
        val model = model?.takeIf { it.isNotBlank() } ?: failure("ACS_CONTACT_MODEL_MISSING", retryable = false)
        val firmware = firmware?.takeIf { it.isNotBlank() } ?: failure("ACS_CONTACT_FIRMWARE_MISSING", retryable = false)
        return AcsContactProvisioningResource(id, model, firmware)
    }

    private fun parse(value: String): AcsContactProvisioningResource = try {
        json.readValue(value, AcsContactProvisioningResource::class.java)
    } catch (_: Exception) {
        failure("ACS_CONTACT_RESOURCE_INVALID", retryable = false)
    }

    private fun failure(code: String, retryable: Boolean = true): Nothing = throw ProvisioningStepException(
        ProvisioningFailure(code, "No se pudo confirmar el contacto TR-069 de la ONU. Consulte el historial de la operación.", retryable),
    )

    private sealed interface Contact {
        data object Waiting : Contact
        data class Ready(val resource: AcsContactProvisioningResource) : Contact
    }

    private companion object {
        const val RESOURCE_KEY = "acs-contact"
    }
}
