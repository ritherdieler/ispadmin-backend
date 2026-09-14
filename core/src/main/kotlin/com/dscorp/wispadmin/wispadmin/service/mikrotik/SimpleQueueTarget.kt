package com.dscorp.wispadmin.wispadmin.service.mikrotik

import com.dscorp.wispadmin.wispadmin.data.model.Subscription
import com.dscorp.wispadmin.wispadmin.data.model.usesSimpleQueue
import com.dscorp.wispadmin.wispadmin.util.isValidIpAddress

object SimpleQueueTarget {

    fun of(subscription: Subscription): String? {
        if (!subscription.accessMode.usesSimpleQueue()) return null
        val ip = subscription.ip?.trim()
        return ip?.takeIf { it.isValidIpAddress() }
    }

    fun printFilter(subscription: Subscription): String? = of(subscription)?.let { "$it/32" }

    fun isManaged(subscription: Subscription): Boolean = of(subscription) != null
}
