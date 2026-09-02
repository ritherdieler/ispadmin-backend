package com.dscorp.wispadmin.oltgateway.adapter

import com.dscorp.wispadmin.netdiag.port.NetDiagOltInventoryPort
import com.dscorp.wispadmin.netdiag.port.NetDiagPonOnu
import com.dscorp.wispadmin.oltgateway.port.OltInventoryPort
import com.dscorp.wispadmin.oltgateway.port.OltOnuSnapshot
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(prefix = "olt.gateway", name = ["enabled"], havingValue = "true")
class NetDiagOltInventoryAdapter(
    private val inventory: OltInventoryPort
) : NetDiagOltInventoryPort {

    override fun findOltId(name: String): Long? = inventory.findOltIdByName(name)

    override fun listOnusOnPon(oltName: String, board: Int, port: Int): List<NetDiagPonOnu> {
        val oltPk = findOltId(oltName) ?: return emptyList()
        return inventory.listConfigured(oltPk)
            .filter { it.board == board && it.port == port }
            .map { it.toSummary() }
    }

    override fun findOnu(oltName: String, board: Int, port: Int, onuIndex: Int): NetDiagPonOnu? {
        val oltPk = findOltId(oltName) ?: return null
        return inventory.findBySlot(oltPk, board, port, onuIndex)?.toSummary()
    }

    private fun OltOnuSnapshot.toSummary(): NetDiagPonOnu = NetDiagPonOnu(
        onuIndex = onuIndex,
        sn = sn,
        runState = runState,
        lastDownCause = lastDownCause,
        onuRxDbm = onuRxDbm?.toDouble()
    )
}
