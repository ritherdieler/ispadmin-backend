package com.dscorp.wispadmin.wispadmin.service.subscription

import com.dscorp.wispadmin.wispadmin.data.model.AccessMode
import com.dscorp.wispadmin.wispadmin.data.model.InstallationType
import com.dscorp.wispadmin.wispadmin.data.model.PppoeProvisionStatus

data class PppoeAltaDecision(
    val accessMode: AccessMode,
    val provisionStatus: PppoeProvisionStatus?
) {
    val needsStaticIp: Boolean get() = accessMode != AccessMode.PPPOE_DYNAMIC
}

object PppoeAltaPolicy {

    const val PPPOE_VLAN = "100"

    fun decide(
        enabled: Boolean,
        installationType: InstallationType?,
        vlan: String?
    ): PppoeAltaDecision {
        if (installationType != InstallationType.FIBER) {
            return PppoeAltaDecision(AccessMode.STATIC_IP, null)
        }
        return PppoeAltaDecision(AccessMode.PPPOE_DYNAMIC, PppoeProvisionStatus.PENDING)
    }
}
