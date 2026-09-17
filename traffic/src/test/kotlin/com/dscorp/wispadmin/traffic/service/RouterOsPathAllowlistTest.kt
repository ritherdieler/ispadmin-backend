package com.dscorp.wispadmin.traffic.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class RouterOsPathAllowlistTest {

    @Test
    fun `allows every path Core already uses and no extras`() {
        val corePaths = setOf(
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

        corePaths.forEach { RouterOsPathAllowlist.requireAllowed(it) }
        assertEquals(corePaths, RouterOsPathAllowlist.PATHS)
    }

    @Test
    fun `rejects paths Core does not use`() {
        assertThrows<IllegalArgumentException> {
            RouterOsPathAllowlist.requireAllowed("/file")
        }
        assertThrows<IllegalArgumentException> {
            RouterOsPathAllowlist.requireAllowed("/system/script")
        }
        assertThrows<IllegalArgumentException> {
            RouterOsPathAllowlist.requireAllowed("/ip/hotspot")
        }
    }
}
