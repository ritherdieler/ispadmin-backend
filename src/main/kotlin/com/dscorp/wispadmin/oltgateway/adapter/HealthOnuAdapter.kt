package com.dscorp.wispadmin.oltgateway.adapter

import com.dscorp.wispadmin.oltgateway.domain.entity.OltMgrOnu
import com.dscorp.wispadmin.oltgateway.domain.repository.OltMgrOltRepository
import com.dscorp.wispadmin.oltgateway.domain.repository.OltMgrOnuRepository
import com.dscorp.wispadmin.servicehealth.port.HealthOnuPort
import com.dscorp.wispadmin.servicehealth.port.HealthOnuRef
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(prefix = "olt.gateway", name = ["enabled"], havingValue = "true")
class HealthOnuAdapter(
    private val onus: OltMgrOnuRepository,
    private val olts: OltMgrOltRepository
) : HealthOnuPort {

    override fun findBySn(sn: String): HealthOnuRef? {
        return onus.findBySnIgnoreCaseAndDeletedAtIsNull(sn).orElse(null)?.toRef()
    }

    override fun findByExternalId(externalId: String): HealthOnuRef? {
        return onus.findByExternalIdAndDeletedAtIsNull(externalId).orElse(null)?.toRef()
    }

    override fun findByOltBoardPortOnu(oltId: Long, board: Int, port: Int, onuIndex: Int): HealthOnuRef? {
        return onus.findByOlt_IdAndBoardAndPortAndOnuIndexAndDeletedAtIsNull(oltId, board, port, onuIndex)
            .orElse(null)?.toRef()
    }

    override fun findByOlt(oltId: Long): List<HealthOnuRef> {
        return onus.findByOlt_IdWithStatus(oltId).filter { it.deletedAt == null }.map { it.toRef() }
    }

    override fun findOltIdByName(name: String): Long? {
        return olts.findByName(name).orElse(null)?.id
    }

    private fun OltMgrOnu.toRef(): HealthOnuRef = HealthOnuRef(
        id = id!!,
        sn = sn,
        externalId = externalId,
        oltId = olt.id,
        oltName = olt.name,
        board = board,
        port = port,
        onuIndex = onuIndex,
        zoneId = zone?.id
    )
}
