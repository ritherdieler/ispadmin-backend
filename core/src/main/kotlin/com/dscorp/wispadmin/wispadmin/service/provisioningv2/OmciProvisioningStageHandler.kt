package com.dscorp.wispadmin.wispadmin.service.provisioningv2

import com.dscorp.wispadmin.wispadmin.oltclient.GatewayOmciManagementCompensateRequest
import com.dscorp.wispadmin.wispadmin.oltclient.GatewayOmciManagementRequest
import com.dscorp.wispadmin.wispadmin.oltclient.GatewayOnuActivationClient
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.web.client.HttpClientErrorException

data class OmciProvisioningResource(
    val tr069ProfileId: Int,
)

/** Configures only management DHCP/VLAN 1000 and TR-069 through the OLT OMCI adapter. */
class OmciProvisioningStageHandler(
    private val gateway: GatewayOnuActivationClient,
    private val json: ObjectMapper,
) : ProvisioningStageHandler {
    override val stage = ProvisioningStage.OMCI

    override fun reconcile(context: ProvisioningStageContext): StageObservation {
        val stored = context.resourceSnapshot(RESOURCE_KEY) ?: return StageObservation.NEEDS_APPLY
        val expected = expected(context)
        val resource = json.readValue(stored, OmciProvisioningResource::class.java)
        if (resource.tr069ProfileId != expected.profileId) failure("OMCI_PROFILE_RESOURCE_MISMATCH", retryable = false)
        context.assertLease()
        ensure(expected)
        return StageObservation.SATISFIED
    }

    override fun apply(context: ProvisioningStageContext): StageObservation {
        val expected = expected(context)
        context.assertLease()
        ensure(expected)
        context.assertLease()
        context.captureResource(RESOURCE_KEY, json.writeValueAsString(OmciProvisioningResource(expected.profileId)))
        return StageObservation.SATISFIED
    }

    override fun compensate(context: ProvisioningStageContext): StageObservation {
        val expected = expected(context)
        try {
            gateway.compensateOmciManagement(GatewayOmciManagementCompensateRequest(
                operationId = context.operation.id,
                sn = context.operation.serial,
                slot = expected.olt.board,
                port = expected.olt.port,
                ontId = expected.olt.ontId,
                tr069ProfileId = expected.profileId,
            ))
        } catch (_: HttpClientErrorException.NotFound) {
            // The owned ONU may already have been removed after an interrupted cancellation.
        }
        return StageObservation.SATISFIED
    }

    private fun ensure(expected: ExpectedManagement) {
        val evidence = gateway.ensureOmciManagement(GatewayOmciManagementRequest(
            sn = expected.operation.serial,
            slot = expected.olt.board,
            port = expected.olt.port,
            ontId = expected.olt.ontId,
            tr069ProfileId = expected.profileId,
        ))
        if (!evidence.configured) failure("OMCI_MANAGEMENT_UNCONFIRMED")
    }

    private fun expected(context: ProvisioningStageContext): ExpectedManagement {
        val registration = context.resourceSnapshot(ProvisioningV2RegistrationService.REGISTRATION_RESOURCE_KEY)
            ?: failure("REGISTRATION_SNAPSHOT_MISSING", retryable = false)
        val olt = context.resourceSnapshot(OLT_RESOURCE_KEY)
            ?: failure("OLT_RESOURCE_MISSING", retryable = false)
        val registrationSnapshot = parseRegistration(registration)
        val oltResource = parseOlt(olt)
        if (registrationSnapshot.tr069ProfileId !in 1..65535) failure("TR069_PROFILE_INVALID", retryable = false)
        return ExpectedManagement(context.operation, oltResource, registrationSnapshot.tr069ProfileId)
    }

    private fun parseRegistration(value: String): ProvisioningV2RegistrationSnapshot = try {
        json.readValue(value, ProvisioningV2RegistrationSnapshot::class.java)
    } catch (_: Exception) {
        failure("REGISTRATION_SNAPSHOT_INVALID", retryable = false)
    }

    private fun parseOlt(value: String): OltProvisioningResource = try {
        json.readValue(value, OltProvisioningResource::class.java)
    } catch (_: Exception) {
        failure("OLT_RESOURCE_INVALID", retryable = false)
    }

    private fun failure(code: String, retryable: Boolean = true): Nothing = throw ProvisioningStepException(
        ProvisioningFailure(code, "No se pudo confirmar la gestión OMCI/TR-069. Consulte el historial de la operación.", retryable),
    )

    private data class ExpectedManagement(
        val operation: ProvisioningOperation,
        val olt: OltProvisioningResource,
        val profileId: Int,
    )

    private companion object {
        const val RESOURCE_KEY = "omci"
        const val OLT_RESOURCE_KEY = "olt"
    }
}
