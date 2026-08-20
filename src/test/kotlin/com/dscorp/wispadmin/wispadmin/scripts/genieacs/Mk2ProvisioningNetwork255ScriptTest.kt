package com.dscorp.wispadmin.wispadmin.scripts.genieacs

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class Mk2ProvisioningNetwork255ScriptTest {

    @Test
    fun script_defines_gateway_dhcp_firewall_and_nat() {
        val root = Path.of(System.getProperty("user.dir"))
        val script = root.resolve("scripts/genieacs/mk2-provisioning-network-255.rsc")
        assertTrue(Files.exists(script), "missing $script")
        val text = Files.readString(script)
        assertTrue(text.contains("192.168.255.1/24"), text)
        assertTrue(text.contains("provisioning-255"), text)
        assertTrue(text.contains("192.168.255.100-192.168.255.250"), text)
        assertTrue(text.contains("8.8.8.8,8.8.4.4"), text)
        assertTrue(text.contains("dst-port=7547"), text)
        assertTrue(text.contains("NAT staging TR-069 255"), text)
        assertTrue(text.contains("LAN_MK1"), text)
    }

    @Test
    fun wg_olt_routes_include_255_subnet() {
        val root = Path.of(System.getProperty("user.dir"))
        val routes = root.resolve("scripts/genieacs/wg-olt-customer-routes.sh")
        val allowed = root.resolve("scripts/genieacs/wg-olt-genieacs-allowedips.example")
        assertTrue(Files.readString(routes).contains("192.168.255.0/24"))
        assertTrue(Files.readString(allowed).contains("192.168.255.0/24"))
    }
}
