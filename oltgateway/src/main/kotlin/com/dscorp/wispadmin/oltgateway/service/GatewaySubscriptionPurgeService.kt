package com.dscorp.wispadmin.oltgateway.service

import com.dscorp.wispadmin.oltgateway.exception.OnuNotFoundException

class GatewaySubscriptionPurgeService(
    private val deleteOnu: (String) -> Unit,
    private val deleteInventory: (String) -> Unit,
) {
    fun purge(sn: String?) {
        val serial = sn?.trim().orEmpty()
        if (serial.isEmpty()) return
        try {
            deleteOnu(serial)
        } catch (_: OnuNotFoundException) {
        }
        deleteInventory(serial)
    }
}
