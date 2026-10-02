package com.dscorp.wispadmin.wispadmin.service.provisioningv2

import com.dscorp.wispadmin.wispadmin.acsclient.AcsCpeCoreClient
import com.dscorp.wispadmin.wispadmin.acsclient.CoreOnboardingV2InternetCompensateRequest
import com.dscorp.wispadmin.wispadmin.acsclient.CoreOnboardingV2InternetRequest
import com.dscorp.wispadmin.wispadmin.acsclient.CoreOnboardingV2InternetStatusRequest
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionRepository
import com.dscorp.wispadmin.wispadmin.data.model.AccessMode
import com.dscorp.wispadmin.wispadmin.service.whatsapp.CrmSecretCipher
import com.dscorp.wispadmin.wispadmin.util.isValidIpAddress
import com.fasterxml.jackson.databind.ObjectMapper

data class InternetProvisioningResource(
    val taskId: String,
    val deviceId: String,
    val model: String,
    val firmware: String,
    val mode: String = "pppoe",
    val ip: String? = null,
)

/** Queues the owned Internet provision; completion is verified by the observed WAN. */
class InternetProvisioningStageHandler(
    private val subscriptions: SubscriptionRepository,
    private val cipher: CrmSecretCipher,
    private val acs: AcsCpeCoreClient,
    private val json: ObjectMapper,
    private val defaultDns: String = "8.8.8.8,8.8.4.4",
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
            mode = expected.mode,
            ip = expected.staticWan?.ip,
            subnetMask = expected.staticWan?.subnetMask,
            gateway = expected.staticWan?.gateway,
            dns = expected.staticWan?.let { defaultDns },
        ))
        if (queued.taskId.isBlank()) failure("ACS_INTERNET_TASK_UNCONFIRMED")
        context.assertLease()
        context.captureResource(RESOURCE_KEY, json.writeValueAsString(InternetProvisioningResource(
            queued.taskId, expected.contact.deviceId, expected.contact.model, expected.contact.firmware,
            expected.mode, expected.staticWan?.ip,
        )))
        return observed(InternetProvisioningResource(queued.taskId, expected.contact.deviceId, expected.contact.model,
            expected.contact.firmware, expected.mode, expected.staticWan?.ip), context.operation, compensation = false)
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
        val subscription = subscriptions.lockIdentityOwner(context.operation.subscriptionId
            ?: failure("SUBSCRIPTION_ID_REQUIRED", retryable = false))
            ?: failure("SUBSCRIPTION_NOT_FOUND", false)
        val staticWan = if (subscription.accessMode == AccessMode.STATIC_IP) staticWan(subscription) else null
        val username = if (staticWan == null) {
            subscription.pppoeUsername?.trim()?.takeIf { it.isNotEmpty() } ?: failure("PPPOE_USERNAME_REQUIRED", false)
        } else ""
        val password = if (staticWan == null) {
            subscription.pppoePasswordEnc?.takeIf(cipher::looksEncrypted)?.let(cipher::decrypt)
                ?: failure("PPPOE_PASSWORD_REQUIRED", false)
        } else ""
        return ExpectedInternet(registration, contact, username, password,
            if (staticWan == null) "pppoe" else "static", staticWan)
    }

    private fun staticWan(subscription: com.dscorp.wispadmin.wispadmin.data.model.Subscription): StaticWan {
        val ip = subscription.ip?.trim()?.takeIf { it.isValidIpAddress() }
            ?: failure("STATIC_IP_REQUIRED", false)
        val segment = subscription.ipPool?.ipSegment?.trim() ?: failure("STATIC_POOL_REQUIRED", false)
        val poolAddress = segment.substringBefore('/').takeIf { it.isValidIpAddress() }
            ?: failure("STATIC_GATEWAY_REQUIRED", false)
        val prefix = segment.substringAfter('/', "").toIntOrNull()?.takeIf { it in 1..30 }
            ?: failure("STATIC_POOL_PREFIX_REQUIRED", false)
        fun number(address: String): Long = address.split('.').fold(0L) { value, octet ->
            (value shl 8) or octet.toLong()
        }
        fun address(value: Long): String = listOf(24, 16, 8, 0).joinToString(".") {
            ((value ushr it) and 0xff).toString()
        }
        val mask = (0xffffffffL shl (32 - prefix)) and 0xffffffffL
        val network = number(poolAddress) and mask
        val assigned = number(ip)
        if ((assigned and mask) != network || assigned == network || assigned == (network or (mask xor 0xffffffffL))) {
            failure("STATIC_IP_OUTSIDE_POOL", false)
        }
        val gateway = address(network + 1)
        if (ip == gateway) failure("STATIC_IP_IS_GATEWAY", false)
        return StaticWan(ip, address(mask), gateway)
    }

    private fun parseRegistration(value: String) = decode<ProvisioningV2RegistrationSnapshot>(value, "REGISTRATION_SNAPSHOT_INVALID")
    private fun parseContact(value: String) = decode<AcsContactProvisioningResource>(value, "ACS_CONTACT_RESOURCE_INVALID")
    private fun parseResource(value: String) = decode<InternetProvisioningResource>(value, "INTERNET_RESOURCE_INVALID")
    private fun observed(resource: InternetProvisioningResource, operation: ProvisioningOperation, compensation: Boolean): StageObservation {
        val response = acs.onboardingV2InternetStatus(CoreOnboardingV2InternetStatusRequest(
            operation.id, operation.serial, resource.deviceId, resource.model, resource.firmware,
            resource.mode, resource.ip,
        ), compensation)
        return when (response.state) {
            "COMPLETE" -> StageObservation.SATISFIED
            "WAITING" -> StageObservation.WAITING
            "FAILED" -> {
                val reason = response.reason?.trim()?.takeIf { it.isNotEmpty() }
                val fallback = if (compensation) "ACS_INTERNET_COMPENSATION_FAILED" else "ACS_INTERNET_TASK_FAILED"
                val code = reason?.takeIf { FAILURE_CODE.matches(it) } ?: fallback
                failure(code, false, reason)
            }
            else -> failure("ACS_INTERNET_STATUS_INVALID")
        }
    }
    private inline fun <reified T> decode(value: String, code: String): T = try { json.readValue(value, T::class.java) } catch (_: Exception) { failure(code, false) }
    private fun failure(code: String, retryable: Boolean = true, reason: String? = null): Nothing = throw ProvisioningStepException(
        ProvisioningFailure(
            code,
            reason?.takeIf { it.isNotBlank() }?.let { "No se pudo aplicar la WAN de Internet: $it" }
                ?: "No se pudo confirmar la tarea de Internet. Consulte el historial de la operación.",
            retryable,
        ))
    private data class StaticWan(val ip: String, val subnetMask: String, val gateway: String)
    private data class ExpectedInternet(
        val registration: ProvisioningV2RegistrationSnapshot,
        val contact: AcsContactProvisioningResource,
        val username: String,
        val password: String,
        val mode: String,
        val staticWan: StaticWan?,
    )
    private companion object {
        const val RESOURCE_KEY = "internet"
        const val CONTACT_RESOURCE_KEY = "acs-contact"
        val FAILURE_CODE = Regex("^[A-Z][A-Z0-9_]{2,80}$")
    }
}
