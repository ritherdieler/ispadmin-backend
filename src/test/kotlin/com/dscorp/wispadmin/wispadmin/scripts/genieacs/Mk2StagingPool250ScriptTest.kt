package com.dscorp.wispadmin.wispadmin.scripts.genieacs

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class Mk2StagingPool250ScriptTest {

    private fun root(): Path = Path.of(System.getProperty("user.dir"))

    private fun scriptText(): String {
        val script = root().resolve("scripts/genieacs/mk2-staging-pool-250.rsc")
        assertTrue(Files.exists(script), "missing $script")
        return Files.readString(script)
    }

    @Test
    fun script_adds_gateway_and_nat_on_sfp_sfpplus2_without_dhcp() {
        val text = scriptText()
        assertTrue(text.contains("192.168.250.1/24"), text)
        assertTrue(text.contains("192.168.250.0/24"), text)
        assertTrue(text.contains(":local iface \"sfp-sfpplus2\""), text)
        assertTrue(text.contains("NAT staging e2e 250"), text)
        assertTrue(text.contains("src-address=192.168.250.0/24"), text)
        assertTrue(text.contains("masquerade"), text)
        assertFalse(text.contains("/ip dhcp-server"), text)
        assertFalse(text.contains("192.168.250.100-"), text)
    }

    @Test
    fun script_isolates_staging_from_legacy_lan() {
        val text = scriptText()
        assertTrue(text.contains("staging-e2e-250"), text)
        assertTrue(text.contains("192.168.22.0/24"), text)
        assertTrue(text.contains("Drop staging 250 to legacy LAN_MK1"), text)
    }
}
