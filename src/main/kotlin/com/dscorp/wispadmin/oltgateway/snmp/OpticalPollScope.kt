package com.dscorp.wispadmin.oltgateway.snmp

import com.dscorp.wispadmin.oltgateway.domain.entity.OltMgrOnu

/**
 * Builds GPON port scope for SNMP optical walks from DB inventory.
 */
object OpticalPollScope {

    fun portsFromOnus(onus: Collection<OltMgrOnu>, onlineOnly: Boolean): Set<GponFsp> {
        return onus
            .asSequence()
            .filter { it.deletedAt == null }
            .filter { !onlineOnly || it.status?.runState == "online" }
            .map { GponFsp(frame = 0, slot = it.board, port = it.port) }
            .toSet()
    }

    /**
     * Scope for the fused inventory+optical pass: every port of every board that holds ONUs.
     * Board-derived, not port-derived, so an ONT on a port with no DB rows is still discovered;
     * run state is ignored because the inventory needs offline ONTs too.
     */
    fun fusedScanPorts(onus: Collection<OltMgrOnu>, portsPerBoard: Int): Set<GponFsp> {
        val ports = portsPerBoard.coerceAtLeast(1)
        return onus
            .asSequence()
            .filter { it.deletedAt == null }
            .filter { it.board >= 0 }
            .map { it.board }
            .distinct()
            .sorted()
            .flatMap { board -> (0 until ports).asSequence().map { GponFsp(frame = 0, slot = board, port = it) } }
            .toSet()
    }
}
