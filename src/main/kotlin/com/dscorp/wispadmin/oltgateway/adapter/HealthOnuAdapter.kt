package com.dscorp.wispadmin.oltgateway.adapter

import com.dscorp.wispadmin.oltgateway.port.OltInventoryPort
import com.dscorp.wispadmin.oltgateway.port.OltOnuSnapshot
import com.dscorp.wispadmin.servicehealth.port.HealthOnuPort
import com.dscorp.wispadmin.servicehealth.port.HealthOnuRef
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(prefix = "olt.gateway", name = ["enabled"], havingValue = "true")
class HealthOnuAdapter(
    private val inventory: OltInventoryPort
) : HealthOnuPort {

    override fun findBySn(sn: String): HealthOnuRef? = inventory.findBySn(sn)?.toRef()

    override fun findByExternalId(externalId: String): HealthOnuRef? =
        inventory.findByExternalId(externalId)?.toRef()

    override fun findByOltBoardPortOnu(oltId: Long, board: Int, port: Int, onuIndex: Int): HealthOnuRef? =
        inventory.findBySlot(oltId, board, port, onuIndex)?.toRef()

    override fun findByOlt(oltId: Long): List<HealthOnuRef> =
        inventory.listConfigured(oltId).map { it.toRef() }

    override fun findOltIdByName(name: String): Long? = inventory.findOltIdByName(name)

    private fun OltOnuSnapshot.toRef(): HealthOnuRef = HealthOnuRef(
        id = id,
        sn = sn,
        externalId = externalId,
        oltId = oltId,
        oltName = oltName,
        board = board,
        port = port,
        onuIndex = onuIndex,
        zoneId = zoneId
    )
}
