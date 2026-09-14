package com.dscorp.wispadmin.traffic.service

import com.dscorp.wispadmin.routeros.RouterOsTrafficCounterParser

object TrafficTargetKey {

    const val PREFIX = "pppoe:"

    private val DYNAMIC_QUEUE_NAME = Regex("^<pppoe-(.+)>$")

    fun of(ip: String?, pppoeUsername: String?): String? {
        pppoeUsername?.trim()?.takeIf { it.isNotEmpty() }?.let { return PREFIX + it }
        return ip?.trim()?.takeIf { it.isNotEmpty() }
    }

    fun ofQueue(name: String?, target: String?): String? {
        val username = DYNAMIC_QUEUE_NAME.find(name?.trim().orEmpty())?.groupValues?.get(1)
        if (username != null) return PREFIX + username
        return queueAddress(target)
    }

    fun queueAddress(target: String?): String? = RouterOsTrafficCounterParser.normalizeTarget(target)

    fun isPppoe(key: String?): Boolean = key?.startsWith(PREFIX) == true

    fun username(key: String?): String? =
        key?.takeIf { it.startsWith(PREFIX) }?.removePrefix(PREFIX)?.takeIf { it.isNotEmpty() }
}
