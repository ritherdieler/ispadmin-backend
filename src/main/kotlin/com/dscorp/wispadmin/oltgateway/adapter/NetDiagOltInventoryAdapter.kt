package com.dscorp.wispadmin.oltgateway.adapter

import com.dscorp.wispadmin.netdiag.port.NetDiagOltInventoryPort
import com.dscorp.wispadmin.netdiag.port.NetDiagPonOnu
import com.dscorp.wispadmin.oltgateway.domain.entity.OltMgrOnu
import com.dscorp.wispadmin.oltgateway.domain.repository.OltMgrOltRepository
import com.dscorp.wispadmin.oltgateway.domain.repository.OltMgrOnuRepository
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(prefix = "olt.gateway", name = ["enabled"], havingValue = "true")
class NetDiagOltInventoryAdapter(
    private val oltRepository: OltMgrOltRepository,
    private val onuRepository: OltMgrOnuRepository
) : NetDiagOltInventoryPort {

    override fun findOltId(name: String): Long? {
        return oltRepository.findByName(name).orElse(null)?.id
    }

    override fun listOnusOnPon(oltName: String, board: Int, port: Int): List<NetDiagPonOnu> {
        val oltPk = findOltId(oltName) ?: return emptyList()
        return onuRepository.findByOlt_IdAndBoardAndPortWithStatus(oltPk, board, port).map { it.toSummary() }
    }

    override fun findOnu(oltName: String, board: Int, port: Int, onuIndex: Int): NetDiagPonOnu? {
        val oltPk = findOltId(oltName) ?: return null
        return onuRepository.findByOlt_IdAndBoardAndPortAndOnuIndexAndDeletedAtIsNull(
            oltPk,
            board,
            port,
            onuIndex
        ).orElse(null)?.toSummary()
    }

    private fun OltMgrOnu.toSummary(): NetDiagPonOnu = NetDiagPonOnu(
        onuIndex = onuIndex,
        sn = sn,
        runState = status?.runState,
        lastDownCause = status?.lastDownCause,
        onuRxDbm = status?.onuRxDbm?.toDouble()
    )
}
