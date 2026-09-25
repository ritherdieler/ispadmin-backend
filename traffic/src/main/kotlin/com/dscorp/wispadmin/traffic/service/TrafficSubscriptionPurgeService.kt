package com.dscorp.wispadmin.traffic.service

class TrafficSubscriptionPurgeService(
    private val deleteBySubscriptionId: (Int) -> Unit,
    private val deleteByClientIp: (String) -> Unit,
) {
    fun purge(subscriptionId: Int, ip: String?, pppoeUsername: String?) {
        deleteBySubscriptionId(subscriptionId)
        ip?.trim()?.takeIf { it.isNotEmpty() }?.let(deleteByClientIp)
        pppoeUsername?.trim()?.takeIf { it.isNotEmpty() }?.let { deleteByClientIp("pppoe:$it") }
    }
}
