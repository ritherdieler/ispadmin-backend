package com.dscorp.wispadmin.oltgateway.service

import com.dscorp.wispadmin.oltgateway.api.SmartOltActionResponseDto
import com.dscorp.wispadmin.oltgateway.exception.OnuNotFoundException
import org.springframework.stereotype.Service

@Service
class OnuGovernanceDeleteService(
    private val manager: OltManagerFacade,
    private val ownerships: ProvisioningV2OnuOwnershipService,
    private val activation: OnuActivationService,
) {
    fun deleteBySn(sn: String): SmartOltActionResponseDto {
        val serial = sn.trim().uppercase()
        if (serial.isEmpty()) throw OnuNotFoundException("ONU serial is blank")
        val ownership = ownerships.find(serial)
        val externalId = manager.externalIdBySn(serial)?.takeIf { it.isNotBlank() }
            ?: ownership?.externalId?.takeIf { it.isNotBlank() }
        if (externalId == null && ownership == null) {
            throw OnuNotFoundException("ONU not found for sn=$serial")
        }
        if (externalId != null) {
            try {
                manager.deleteOnu(externalId)
            } catch (_: OnuNotFoundException) {
                // Inventory row already gone; the reservation still has to be released.
            }
        }
        ownerships.releaseSerial(serial)
        activation.clearJournal(serial)
        return SmartOltActionResponseDto(status = true, unique_external_id = externalId)
    }
}
