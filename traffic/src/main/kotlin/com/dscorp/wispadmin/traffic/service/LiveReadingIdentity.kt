package com.dscorp.wispadmin.traffic.service

data class LiveReadingIdentity(
    val subscriptionId: Int,
    val accessMode: String,
    val ip: String? = null,
    val pppoeLastIp: String? = null,
    val pppoeUsername: String? = null,
    val hostDeviceId: Int? = null,
    val envTag: String? = null,
) {
    fun usesSimpleQueue(): Boolean {
        return accessMode != "PPPOE_DYNAMIC"
    }
}
