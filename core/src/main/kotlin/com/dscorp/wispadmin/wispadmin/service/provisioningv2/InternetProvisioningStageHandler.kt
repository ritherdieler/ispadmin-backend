package com.dscorp.wispadmin.wispadmin.service.provisioningv2

import com.dscorp.wispadmin.wispadmin.acsclient.AcsCpeCoreClient
import com.dscorp.wispadmin.wispadmin.acsclient.CoreOnboardingV2InternetCompensateRequest
import com.dscorp.wispadmin.wispadmin.acsclient.CoreOnboardingV2InternetRequest
import com.dscorp.wispadmin.wispadmin.acsclient.CoreOnboardingV2InternetStatusRequest
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionRepository
import com.dscorp.wispadmin.wispadmin.service.whatsapp.CrmSecretCipher
import com.fasterxml.jackson.databind.ObjectMapper

data class InternetProvisioningResource(
    val taskId: String,
    val deviceId: String,
    val model: String,
    val firmware: String,
)

/** Queues the owned PPPoE provision; completion is intentionally verified by VERIFY, never assumed here. */
class InternetProvisioningStageHandler(
    private val subscriptions: SubscriptionRepository,
    private val cipher: CrmSecretCipher,
    private val acs: AcsCpeCoreClient,
    private val json: ObjectMapper,
) : ProvisioningStageHandler {
    override val stage = ProvisioningStage.INTERNET

    override fun reconcile(context: ProvisioningStageContext): StageObservation {
        val stored = context.resourceSnapshot(RESOURCE_KEY) ?: return StageObservation.NEEDS_APPLY
        val resource = parseResource(stored)
        val expected = expected(context)
        if (resource.deviceId != expected.contact.deviceId || resource.model != expected.contact.model ||
            resource.firmware != expected.contact.firmware) failure("INTERNET_RESOURCE_IDENTITY_CHANGED", false)
        return observed(resource, context.operation, compensation = false)
    }

    override fun apply(context: ProvisioningStageContext): StageObservation {
        val expected = expected(context)
        context.assertLease()
        val queued = acs.enqueueOnboardingV2Internet(CoreOnboardingV2InternetRequest(
            operationId = context.operation.id,
            sn = context.operation.serial,
            deviceId = expected.contact.deviceId,
            model = expected.contact.model,
            firmware = expected.contact.firmware,
            username = expected.username,
            password = expected.password,
            vlan = expected.registration.internetVlan,
        ))
        if (queued.taskId.isBlank()) failure("ACS_INTERNET_TASK_UNCONFIRMED")
        context.assertLease()
        context.captureResource(RESOURCE_KEY, json.writeValueAsString(InternetProvisioningResource(
            queued.taskId, expected.contact.deviceId, expected.contact.model, expected.contact.firmware,
        )))
        return observed(InternetProvisioningResource(queued.taskId, expected.contact.deviceId, expected.contact.model, expected.contact.firmware), context.operation, compensation = false)
    }

    override fun compensate(context: ProvisioningStageContext): StageObservation {
        val resource = context.resourceSnapshot(RESOURCE_KEY) ?: return StageObservation.SATISFIED
        val parsed = parseResource(resource)
        acs.compensateOnboardingV2Internet(CoreOnboardingV2InternetCompensateRequest(
            context.operation.id, context.operation.serial, parsed.deviceId, parsed.model, parsed.firmware,
        ))
        return observed(parsed, context.operation, compensation = true)
    }

    private fun expected(context: ProvisioningStageContext): ExpectedInternet {
        val registration = parseRegistration(context.resourceSnapshot(ProvisioningV2RegistrationService.REGISTRATION_RESOURCE_KEY)
            ?: failure("REGISTRATION_SNAPSHOT_MISSING", false))
        val contact = parseContact(context.resourceSnapshot(CONTACT_RESOURCE_KEY) ?: failure("ACS_CONTACT_RESOURCE_MISSING", false))
        val subscription = subscriptions.lockIdentityOwner(context.operation.subscriptionId)
            ?: failure("SUBSCRIPTION_NOT_FOUND", false)
        val username = subscription.pppoeUsername?.trim()?.takeIf { it.isNotEmpty() } ?: failure("PPPOE_USERNAME_REQUIRED", false)
        val password = subscription.pppoePasswordEnc?.takeIf(cipher::looksEncrypted)?.let(cipher::decrypt)
            ?: failure("PPPOE_PASSWORD_REQUIRED", false)
        return ExpectedInternet(registration, contact, username, password)
    }

    private fun parseRegistration(value: String) = decode<ProvisioningV2RegistrationSnapshot>(value, "REGISTRATION_SNAPSHOT_INVALID")
    private fun parseContact(value: String) = decode<AcsContactProvisioningResource>(value, "ACS_CONTACT_RESOURCE_INVALID")
    private fun parseResource(value: String) = decode<InternetProvisioningResource>(value, "INTERNET_RESOURCE_INVALID")
    private fun observed(resource: InternetProvisioningResource, operation: ProvisioningOperation, compensation: Boolean): StageObservation {
        val response = acs.onboardingV2InternetStatus(CoreOnboardingV2InternetStatusRequest(
            operation.id, operation.serial, resource.deviceId, resource.model, resource.firmware,
        ), compensation)
        return when (response.state) {
            "COMPLETE" -> StageObservation.SATISFIED
            "WAITING" -> StageObservation.WAITING
            "FAILED" -> failure(if (compensation) "ACS_INTERNET_COMPENSATION_FAILED" else "ACS_INTERNET_TASK_FAILED", false)
            else -> failure("ACS_INTERNET_STATUS_INVALID")
        }
    }
    private inline fun <reified T> decode(value: String, code: String): T = try { json.readValue(value, T::class.java) } catch (_: Exception) { failure(code, false) }
    private fun failure(code: String, retryable: Boolean = true): Nothing = throw ProvisioningStepException(
        ProvisioningFailure(code, "No se pudo confirmar la tarea PPPoE. Consulte el historial de la operación.", retryable))
    private data class ExpectedInternet(val registration: ProvisioningV2RegistrationSnapshot, val contact: AcsContactProvisioningResource, val username: String, val password: String)
    private companion object { const val RESOURCE_KEY = "internet"; const val CONTACT_RESOURCE_KEY = "acs-contact" }
}
