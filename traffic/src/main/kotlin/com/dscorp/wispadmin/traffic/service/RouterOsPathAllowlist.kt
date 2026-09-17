package com.dscorp.wispadmin.traffic.service

object RouterOsPathAllowlist {
    val PATHS = setOf(
        "/queue/simple",
        "/ppp/secret",
        "/ppp/active",
        "/ppp/profile",
        "/interface",
        "/interface/monitor-traffic",
        "/interface/ethernet/monitor",
        "/system/resource",
        "/system/identity",
        "/system/package",
        "/system/health",
        "/system/routerboard",
        "/ip/firewall/address-list",
        "/ip/firewall/filter",
        "/ip/address",
        "/tool/netwatch",
    )

    fun requireAllowed(path: String) {
        val normalized = path.trim()
        if (normalized !in PATHS) {
            throw IllegalArgumentException("RouterOS path not allowed: $path")
        }
    }
}
