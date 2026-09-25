package com.dscorp.wispadmin.oltgateway.controller

import com.dscorp.wispadmin.oltgateway.service.OmciManagementEvidence
import com.dscorp.wispadmin.oltgateway.service.OmciManagementTarget
import com.dscorp.wispadmin.oltgateway.service.OmciManagementV2
import com.dscorp.wispadmin.oltgateway.service.ProvisioningV2OnuOwnershipService
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class OmciManagementRequest(
    val sn: String,
    val slot: Int,
    val port: Int,
    val ontId: Int,
    val tr069ProfileId: Int,
)

data class OmciManagementCompensateRequest(
    val operationId: String,
    val sn: String,
    val slot: Int,
    val port: Int,
    val ontId: Int,
    val tr069ProfileId: Int,
)

@RestController
@RequestMapping("/api/olt-gateway/onus")
@ConditionalOnBean(OmciManagementV2::class)
class OnuOmciManagementController(
    private val management: OmciManagementV2,
    private val ownerships: ProvisioningV2OnuOwnershipService,
) {
    @PostMapping("/{sn}/omci/management")
    fun ensureManagement(
        @PathVariable sn: String,
        @RequestBody request: OmciManagementRequest,
    ): OmciManagementEvidence {
        require(sn.equals(request.sn, ignoreCase = true)) { "ONU_SERIAL_MISMATCH" }
        return management.ensure(OmciManagementTarget(sn.uppercase(), request.slot, request.port, request.ontId,
            request.tr069ProfileId))
    }

    @PostMapping("/{sn}/omci/management/compensate")
    fun compensateManagement(
        @PathVariable sn: String,
        @RequestBody request: OmciManagementCompensateRequest,
    ) {
        require(sn.equals(request.sn, ignoreCase = true)) { "ONU_SERIAL_MISMATCH" }
        val serial = sn.uppercase()
        val ownership = ownerships.requireOwner(serial, request.operationId)
        require(ownership.board == request.slot && ownership.port == request.port && ownership.ontId == request.ontId) {
            "ONU_TARGET_OWNERSHIP_MISMATCH"
        }
        management.remove(OmciManagementTarget(serial, request.slot, request.port, request.ontId, request.tr069ProfileId))
    }
}
