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
}
