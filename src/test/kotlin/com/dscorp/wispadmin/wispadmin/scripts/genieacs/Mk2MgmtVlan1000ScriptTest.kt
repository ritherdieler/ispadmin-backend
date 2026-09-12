package com.dscorp.wispadmin.wispadmin.scripts.genieacs

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class Mk2MgmtVlan1000ScriptTest {

    private fun root(): Path = Path.of(System.getProperty("user.dir"))

    private fun scriptText(): String {
        val script = root().resolve("scripts/genieacs/mk2-mgmt-vlan-1000.rsc")
        assertTrue(Files.exists(script), "missing $script")
        return Files.readString(script)
    }

    @Test
    fun script_creates_tagged_vlan1000_subinterface_on_sfp_sfpplus2() {
        val text = scriptText()
        assertTrue(text.contains("vlan1000-olt"), text)
        assertTrue(text.contains("vlan-id=1000"), text)
        assertTrue(text.contains(":local iface \"sfp-sfpplus2\""), text)
        assertTrue(text.contains("0/3/2"), text)
    }

    @Test
    fun script_defines_both_management_prefixes() {
        val text = scriptText()
        assertTrue(text.contains("10.20.0.1/22"), text)
        assertTrue(text.contains("10.20.250.1/24"), text)
        assertTrue(text.contains("10.20.0.0/22"), text)
        assertTrue(text.contains("10.20.250.0/24"), text)
    }

    @Test
    fun script_defines_dhcp_pool_network_and_server() {
        val text = scriptText()
        assertTrue(text.contains("mgmt-1000"), text)
        assertTrue(text.contains("10.20.0.2-10.20.3.254"), text)
        assertTrue(text.contains("8.8.8.8,8.8.4.4"), text)
        assertTrue(text.contains("lease-time=1h"), text)
        assertTrue(text.contains("interface=\$mgmtIf"), "DHCP must bind the VLAN subinterface: $text")
        assertFalse(
            text.contains("dhcp-server add name=dhcp-mgmt-1000 interface=\$iface"),
            "DHCP must not bind the physical port: $text"
        )
    }

    @Test
    fun script_defines_interface_list_with_only_the_vlan_subinterface() {
        val text = scriptText()
        assertTrue(text.contains("OLT-VLAN1000"), text)
        assertFalse(
            text.contains("list=OLT-VLAN1000 interface=sfp-sfpplus2"),
            "the physical port must not join OLT-VLAN1000: $text"
        )
    }

    @Test
    fun script_defines_nat_connection_request_and_isolation() {
        val text = scriptText()
        assertTrue(text.contains("NAT mgmt 1000"), text)
        assertTrue(text.contains("dst-port=7547"), text)
        assertTrue(text.contains("src-address=10.255.255.2"), text)
        assertTrue(text.contains("wg-ispadmin-vps"), text)
        assertTrue(text.contains("192.168.22.0/24"), text)
        assertTrue(text.contains("mgmt-1000-nets"), text)
    }

    @Test
    fun script_does_not_touch_vlan100_provisioning() {
        val text = scriptText()
        assertFalse(text.contains("dhcp-provisioning-255"), "must not touch VLAN 100 DHCP: $text")
        assertFalse(text.contains("192.168.255.1"), "must not touch VLAN 100 gateway: $text")
        assertFalse(text.contains("192.168.250.1"), "must not touch VLAN 100 staging gateway: $text")
        assertFalse(text.contains("vlan100-olt"), "must not reference vlan100-olt: $text")
    }

    @Test
    fun wg_olt_routes_include_management_prefixes() {
        val routes = Files.readString(root().resolve("scripts/genieacs/wg-olt-customer-routes.sh"))
        val allowed = Files.readString(root().resolve("scripts/genieacs/wg-olt-genieacs-allowedips.example"))
        assertTrue(routes.contains("10.20.0.0/22"), routes)
        assertTrue(routes.contains("10.20.250.0/24"), routes)
        assertTrue(allowed.contains("10.20.0.0/22"), allowed)
        assertTrue(allowed.contains("10.20.250.0/24"), allowed)
    }
}
