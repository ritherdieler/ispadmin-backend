package com.dscorp.wispadmin.wispadmin.service.provisioningv2

import com.dscorp.wispadmin.wispadmin.acsclient.*
import com.fasterxml.jackson.databind.ObjectMapper

data class WifiProvisioningResource(val taskId: String, val deviceId: String, val model: String, val firmware: String)

/** ACS owns the encrypted prior Wi-Fi state; Core retains only task evidence. */
class WifiProvisioningStageHandler(private val acs: AcsCpeCoreClient, private val json: ObjectMapper) : ProvisioningStageHandler {
    override val stage = ProvisioningStage.WIFI
    override fun reconcile(context: ProvisioningStageContext): StageObservation {
        val resource = context.resourceSnapshot(KEY)?.let(::parse) ?: return StageObservation.NEEDS_APPLY
        return observed(resource, context.operation, false)
    }
    override fun apply(context: ProvisioningStageContext): StageObservation {
        val registration = parseRegistration(context.resourceSnapshot(ProvisioningV2RegistrationService.REGISTRATION_RESOURCE_KEY) ?: fail("REGISTRATION_SNAPSHOT_MISSING", false))
        val contact = parseContact(context.resourceSnapshot(CONTACT_KEY) ?: fail("ACS_CONTACT_RESOURCE_MISSING", false))
        val ssid24 = registration.wifiSsid24?.takeIf { it.isNotBlank() } ?: fail("WIFI_24_SSID_REQUIRED", false)
        val pass24 = registration.wifiPassword24?.takeIf { it.length in 8..63 } ?: fail("WIFI_24_PASSWORD_REQUIRED", false)
        val ssid5 = registration.wifiSsid5?.takeIf { it.isNotBlank() } ?: fail("WIFI_5_SSID_REQUIRED", false)
        val pass5 = registration.wifiPassword5?.takeIf { it.length in 8..63 } ?: fail("WIFI_5_PASSWORD_REQUIRED", false)
        val queued = acs.enqueueOnboardingV2Wifi(CoreOnboardingV2WifiRequest(context.operation.id, context.operation.serial, contact.deviceId, contact.model, contact.firmware, ssid24, pass24, ssid5, pass5))
        val resource = WifiProvisioningResource(queued.taskId, contact.deviceId, contact.model, contact.firmware)
        context.captureResource(KEY, json.writeValueAsString(resource))
        return observed(resource, context.operation, false)
    }
    override fun compensate(context: ProvisioningStageContext): StageObservation {
        val resource = context.resourceSnapshot(KEY)?.let(::parse) ?: return StageObservation.SATISFIED
        acs.compensateOnboardingV2Wifi(CoreOnboardingV2WifiCompensateRequest(context.operation.id, context.operation.serial, resource.deviceId, resource.model, resource.firmware))
        return observed(resource, context.operation, true)
    }
    private fun observed(r: WifiProvisioningResource, operation: ProvisioningOperation, compensation: Boolean): StageObservation {
        val state = acs.onboardingV2WifiStatus(CoreOnboardingV2WifiCompensateRequest(operation.id, operation.serial, r.deviceId, r.model, r.firmware), compensation).state
        return when (state) { "COMPLETE" -> StageObservation.SATISFIED; "WAITING" -> StageObservation.WAITING; "FAILED" -> fail("ACS_WIFI_TASK_FAILED", false); else -> fail("ACS_WIFI_STATUS_INVALID") }
    }
    private fun parse(value: String): WifiProvisioningResource = decode(value, "WIFI_RESOURCE_INVALID")
    private fun parseRegistration(value: String): ProvisioningV2RegistrationSnapshot = decode(value, "REGISTRATION_SNAPSHOT_INVALID")
    private fun parseContact(value: String): AcsContactProvisioningResource = decode(value, "ACS_CONTACT_RESOURCE_INVALID")
    private inline fun <reified T> decode(value: String, code: String): T = try { json.readValue(value, T::class.java) } catch (_: Exception) { fail(code, false) }
    private fun fail(code: String, retryable: Boolean = true): Nothing = throw ProvisioningStepException(ProvisioningFailure(code, "No se pudo confirmar Wi-Fi. Consulte el historial de la operación.", retryable))
    private companion object { const val KEY = "wifi"; const val CONTACT_KEY = "acs-contact" }
}
