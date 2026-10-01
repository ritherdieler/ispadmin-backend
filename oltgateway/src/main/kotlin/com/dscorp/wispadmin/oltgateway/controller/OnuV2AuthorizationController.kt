package com.dscorp.wispadmin.oltgateway.controller

import com.dscorp.wispadmin.oltgateway.api.AuthorizeOnuFormDto
import com.dscorp.wispadmin.oltgateway.service.OltManagerFacade
import com.dscorp.wispadmin.oltgateway.service.OltServicePortService
import com.dscorp.wispadmin.oltgateway.service.ProvisioningV2OnuOwnership
import com.dscorp.wispadmin.oltgateway.service.ProvisioningV2OnuOwnershipService
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

data class OnuV2AuthorizeRequest(
    val operationId: String,
    val sn: String,
    val oltId: String,
    val ponType: String,
    val board: String,
    val port: String,
    val vlan: String,
    val onuType: String,
    val subscriberName: String,
    val zone: String = "Zone 1",
    val onuMode: String = "Routing",
    val customProfile: String = "Generic_1",
)

data class OnuV2AuthorizeResponse(
    val externalId: String,
    val board: Int,
    val port: Int,
    val ontId: Int,
    val managementVlanReady: Boolean,
)

data class OnuV2CompensateRequest(
    val operationId: String,
    val sn: String,
    val expectedExternalId: String? = null,
)

data class OnuV2CompensateResponse(
    val externalId: String?,
    val deleted: Boolean,
)

/** Authorizes the ONU and the management VLAN. ACS and CPE run in later provisioning stages. */
@RestController
@RequestMapping("/api/olt-gateway/onus/provisioning")
class OnuV2AuthorizationController(
    private val manager: OltManagerFacade,
    private val servicePorts: OltServicePortService,
    private val ownerships: ProvisioningV2OnuOwnershipService,
) {
    @PostMapping("/authorize")
    fun authorize(@RequestBody request: OnuV2AuthorizeRequest): OnuV2AuthorizeResponse {
        require(request.operationId.matches(Regex("[a-zA-Z0-9-]{8,64}"))) { "INVALID_OPERATION_ID" }
        val serial = request.sn.trim().uppercase()
        require(serial.matches(Regex("[A-Z0-9]{12,16}"))) { "INVALID_ONU_SERIAL" }
        val onOlt = runCatching { manager.externalIdBySn(serial) }.getOrNull()?.takeIf { it.isNotBlank() }
        if (onOlt == null) ownerships.releaseSerial(serial)
        val fingerprint = ownerships.fingerprint(listOf(
            serial, request.oltId, request.ponType, request.board, request.port, request.vlan,
            request.onuType, request.subscriberName, request.zone, request.onuMode, request.customProfile,
        ))
        var ownership = ownerships.claim(serial, request.operationId, fingerprint)
        ownership = reconcileOrAuthorize(ownership, request)
        val ports = servicePorts.ensureMgmtVlan(
            sn = serial,
            vlan = MANAGEMENT_VLAN,
            includeTrafficTables = false,
        )
        ownership = ownerships.markManagementReady(ownership)
        return OnuV2AuthorizeResponse(
            externalId = requireNotNull(ownership.externalId),
            board = ports.board,
            port = ports.port,
            ontId = ports.ontId,
            managementVlanReady = MANAGEMENT_VLAN in ports.vlans,
        )
    }

    @PostMapping("/compensate")
    fun compensate(@RequestBody request: OnuV2CompensateRequest): OnuV2CompensateResponse {
        require(request.operationId.matches(Regex("[a-zA-Z0-9-]{8,64}"))) { "INVALID_OPERATION_ID" }
        val serial = request.sn.trim().uppercase()
        require(serial.matches(Regex("[A-Z0-9]{12,16}"))) { "INVALID_ONU_SERIAL" }
        val ownership = ownerships.find(serial)
        val configuredExternalId = manager.externalIdBySn(serial)?.takeIf(String::isNotBlank)

        if (ownership == null) {
            if (configuredExternalId == null) {
                return OnuV2CompensateResponse(externalId = null, deleted = true)
            }
            checkExpectedIdentity(request.expectedExternalId, configuredExternalId, required = true)
            deleteAndConfirm(serial, configuredExternalId)
            return OnuV2CompensateResponse(externalId = configuredExternalId, deleted = true)
        }

        val owned = ownerships.requireOwner(serial, request.operationId)
        owned.externalId?.let { actual ->
            checkExpectedIdentity(request.expectedExternalId, actual, required = false)
        }
        if (configuredExternalId != null) {
            if (!owned.externalId.isNullOrBlank() && owned.externalId != configuredExternalId) {
                throw ResponseStatusException(HttpStatus.CONFLICT, "Configured ONU does not match its reservation")
            }
            checkExpectedIdentity(request.expectedExternalId, configuredExternalId, required = false)
            deleteAndConfirm(serial, configuredExternalId)
        }
        ownerships.releaseAfterConfirmedCleanup(ownership)
        return OnuV2CompensateResponse(externalId = configuredExternalId ?: owned.externalId, deleted = true)
    }

    private fun checkExpectedIdentity(
        expectedExternalId: String?,
        actualExternalId: String,
        required: Boolean,
    ) {
        if ((required && expectedExternalId.isNullOrBlank()) ||
            (!expectedExternalId.isNullOrBlank() && expectedExternalId != actualExternalId)
        ) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Configured ONU does not match the cancelled operation")
        }
    }

    private fun deleteAndConfirm(serial: String, externalId: String) {
        val deleted = manager.deleteOnu(externalId)
        if (!deleted.status || !manager.externalIdBySn(serial).isNullOrBlank()) {
            throw ResponseStatusException(HttpStatus.BAD_GATEWAY, "OLT v2 ONU deletion was not confirmed")
        }
    }

    private fun reconcileOrAuthorize(
        ownership: ProvisioningV2OnuOwnership,
        request: OnuV2AuthorizeRequest,
    ): ProvisioningV2OnuOwnership {
        if (!ownership.externalId.isNullOrBlank()) return ownership

        val importedExternalId = runCatching {
            manager.getOnusDetailsBySn(ownership.serial)
            manager.externalIdBySn(ownership.serial)
        }.getOrNull()?.takeIf { it.isNotBlank() }
        if (!importedExternalId.isNullOrBlank()) {
            if (ownership.stage == "CLAIMED") {
                throw ResponseStatusException(HttpStatus.CONFLICT, "ONU already exists outside this provisioning operation")
            }
            val ports = servicePorts.listBySn(ownership.serial)
            return ownerships.recordAuthorization(ownership, importedExternalId, ports.board, ports.port, ports.ontId)
        }

        val result = manager.authorizeOnu(AuthorizeOnuFormDto(
            olt_id = request.oltId,
            pon_type = request.ponType,
            board = request.board,
            port = request.port,
            sn = ownership.serial,
            vlan = request.vlan,
            onu_type = request.onuType,
            zone = request.zone,
            name = request.subscriberName,
            onu_mode = request.onuMode,
            custom_profile = request.customProfile,
        ))
        val externalId = result.unique_external_id?.takeIf { result.status && it.isNotBlank() }
            ?: throw ResponseStatusException(HttpStatus.BAD_GATEWAY, "OLT v2 authorization was not confirmed")
        val ports = servicePorts.listBySn(ownership.serial)
        return ownerships.recordAuthorization(ownership, externalId, ports.board, ports.port, ports.ontId)
    }

    private companion object {
        const val MANAGEMENT_VLAN = 1000
    }
}
