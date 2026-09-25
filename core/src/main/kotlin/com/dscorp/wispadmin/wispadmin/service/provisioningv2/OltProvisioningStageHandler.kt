package com.dscorp.wispadmin.wispadmin.service.provisioningv2

import com.dscorp.wispadmin.wispadmin.oltclient.GatewayOnuActivationClient
import com.dscorp.wispadmin.wispadmin.oltclient.GatewayOnuV2AuthorizeRequest
import com.dscorp.wispadmin.wispadmin.oltclient.GatewayOnuV2AuthorizeResponse
import com.dscorp.wispadmin.wispadmin.oltclient.GatewayOnuV2CompensateRequest
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpStatusCodeException

data class OltProvisioningResource(
    val externalId: String,
    val board: Int,
    val port: Int,
    val ontId: Int,
)

/**
 * Authorizes only the owned ONU and its VLAN-1000 management service-port.
 * The Gateway's durable fence closes the interval between the remote write and
 * Core's encrypted resource capture, so a retry cannot adopt a foreign ONU.
 */
class OltProvisioningStageHandler(
    private val subscriptions: SubscriptionRepository,
    private val gateway: GatewayOnuActivationClient,
    private val json: ObjectMapper,
) : ProvisioningStageHandler {
    override val stage = ProvisioningStage.OLT

    override fun reconcile(context: ProvisioningStageContext): StageObservation {
        val stored = context.resourceSnapshot(RESOURCE_KEY) ?: return StageObservation.NEEDS_APPLY
        val expected = expected(context)
        context.assertLease()
        val confirmed = authorize(expected)
        val resource = json.readValue(stored, OltProvisioningResource::class.java)
        if (resource != confirmed) conflict("OLT_RESOURCE_MISMATCH")
        return StageObservation.SATISFIED
    }

    override fun apply(context: ProvisioningStageContext): StageObservation {
        context.assertLease()
        val confirmed = authorize(expected(context))
        context.assertLease()
        context.captureResource(RESOURCE_KEY, json.writeValueAsString(confirmed))
        return StageObservation.SATISFIED
    }

    override fun compensate(context: ProvisioningStageContext): StageObservation {
        try {
            val result = gateway.compensateV2(GatewayOnuV2CompensateRequest(
                operationId = context.operation.id,
                sn = context.operation.serial,
            ))
            check(result.deleted) { "OLT_DELETE_UNCONFIRMED" }
        } catch (_: HttpClientErrorException.NotFound) {
            // No Gateway reservation means no v2 OLT effect was ever claimed.
        }
        return StageObservation.SATISFIED
    }

    private fun authorize(expected: ExpectedOnu): OltProvisioningResource {
        try {
            val response = gateway.authorizeV2(GatewayOnuV2AuthorizeRequest(
                operationId = expected.operation.id,
                sn = expected.operation.serial,
                oltId = expected.snapshot.onu.oltId,
                ponType = expected.snapshot.onu.ponType,
                board = expected.snapshot.onu.board,
                port = expected.snapshot.onu.port,
                vlan = expected.snapshot.internetVlan.toString(),
                onuType = expected.snapshot.onu.onuType,
                subscriberName = expected.subscriberName,
            ))
            return response.toResource()
        } catch (ex: HttpStatusCodeException) {
            if (ex.statusCode.value() == 409) {
                throw ProvisioningStepException(ProvisioningFailure(
                    "ONU_ALREADY_RESERVED",
                    "La ONU ya está registrada por otra alta. Libérala en la OLT antes de reintentar.",
                    false,
                ))
            }
            throw ex
        }
    }

    private fun expected(context: ProvisioningStageContext): ExpectedOnu {
        val operation = context.operation
        val snapshot = contextSnapshot(context)
        val subscription = subscriptions.lockIdentityOwner(operation.subscriptionId)
            ?: failure("SUBSCRIPTION_NOT_FOUND", retryable = false)
        if (!subscription.fiberOnuSn.equals(operation.serial, ignoreCase = true)) {
            failure("ONU_IDENTITY_MISMATCH", retryable = false)
        }
        val name = listOfNotNull(subscription.firstName?.trim(), subscription.lastName?.trim())
            .filter { it.isNotEmpty() }
            .joinToString(" ")
            .ifBlank { "Subscription-${operation.subscriptionId}" }
        return ExpectedOnu(operation, snapshot, name)
    }

    private fun contextSnapshot(context: ProvisioningStageContext): ProvisioningV2RegistrationSnapshot {
        // Registration has been captured before scheduling this operation; it is the only
        // source for ONU targeting and is encrypted in the resource store.
        val snapshot = context.resourceSnapshot(ProvisioningV2RegistrationService.REGISTRATION_RESOURCE_KEY)
            ?: failure("REGISTRATION_SNAPSHOT_MISSING", retryable = false)
        return try {
            json.readValue(snapshot, ProvisioningV2RegistrationSnapshot::class.java)
        } catch (_: Exception) {
            failure("REGISTRATION_SNAPSHOT_INVALID", retryable = false)
        }
    }

    private fun GatewayOnuV2AuthorizeResponse.toResource(): OltProvisioningResource {
        if (externalId.isBlank() || board < 0 || port < 0 || ontId < 0 || !managementVlanReady) {
            failure("OLT_MANAGEMENT_UNCONFIRMED")
        }
        return OltProvisioningResource(externalId, board, port, ontId)
    }

    private fun conflict(code: String): Nothing = failure(code, retryable = false)
    private fun failure(code: String, retryable: Boolean = true): Nothing = throw ProvisioningStepException(
        ProvisioningFailure(code, "No se pudo confirmar la autorización OLT de la ONU. Consulte el historial de la operación.", retryable),
    )

    private data class ExpectedOnu(
        val operation: ProvisioningOperation,
        val snapshot: ProvisioningV2RegistrationSnapshot,
        val subscriberName: String,
    )

    private companion object {
        const val RESOURCE_KEY = "olt"
    }
}
