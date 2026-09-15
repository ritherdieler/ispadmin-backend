package com.dscorp.wispadmin.traffic.service

import com.dscorp.wispadmin.traffic.port.TrafficDirectoryTarget

data class ResolvedLiveMonitorTarget(
    val subscriptionId: Int,
    val ip: String,
    val pppoeUsername: String?,
    val routerHint: Int?,
)

object SubscriptionLiveMonitorTarget {

    fun resolve(
        subscriptionId: Int,
        request: Map<String, Any>,
        directory: TrafficDirectoryTarget?,
    ): ResolvedLiveMonitorTarget? {
        val requestIp = stringValue(request["ip"])
        val requestPppoe = stringValue(request["pppoeUsername"])
        val requestHint = intValue(request["routerHint"])
        val ip: String
        val pppoe: String?
        when {
            requestIp != null && requestPppoe == null -> {
                ip = requestIp
                pppoe = null
            }
            requestPppoe != null && requestIp == null -> {
                ip = ""
                pppoe = requestPppoe
            }
            else -> {
                ip = directory?.ip?.trim().orEmpty()
                pppoe = directory?.pppoeUsername?.trim()?.takeIf { it.isNotEmpty() }
            }
        }
        if (TrafficTargetKey.of(ip, pppoe) == null) return null
        return ResolvedLiveMonitorTarget(
            subscriptionId = subscriptionId,
            ip = ip,
            pppoeUsername = pppoe,
            routerHint = requestHint ?: directory?.routerHint,
        )
    }

    private fun stringValue(value: Any?): String? = when (value) {
        is String -> value.trim().takeIf { it.isNotEmpty() }
        else -> null
    }

    private fun intValue(value: Any?): Int? = when (value) {
        is Number -> value.toInt()
        is String -> value.trim().toIntOrNull()
        else -> null
    }
}
