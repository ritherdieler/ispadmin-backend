package com.dscorp.wispadmin.wispadmin.service.mikrotik

import com.dscorp.wispadmin.wispadmin.data.model.Subscription
import com.dscorp.wispadmin.wispadmin.data.model.usesAddressListCut
import com.dscorp.wispadmin.wispadmin.util.isValidIpAddress

object CutListEligibility {

    fun cutListIp(subscription: Subscription): String? {
        if (!subscription.accessMode.usesAddressListCut()) return null
        val ip = subscription.ip?.trim()
        return ip?.takeIf { it.isValidIpAddress() }
    }
}
