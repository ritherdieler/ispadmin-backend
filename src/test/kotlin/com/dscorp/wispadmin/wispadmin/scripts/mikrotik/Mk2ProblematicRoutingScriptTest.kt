package com.dscorp.wispadmin.wispadmin.scripts.mikrotik

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class Mk2ProblematicRoutingScriptTest {

    @Test
    fun mk2_problematic_routing_script_exists_and_preserves_main_ip() {
        val root = Path.of(System.getProperty("user.dir"))
        val routing = root.resolve("scripts/mikrotik-mk2-problematic-routing.rsc")
        val addressList = root.resolve("scripts/mikrotik-mk2-problematic-address-list.rsc")
        val disableMk1 = root.resolve("scripts/mikrotik-mk1-problematic-disable.rsc")

        assertTrue(Files.exists(routing), "missing $routing")
        assertTrue(Files.exists(addressList), "missing $addressList")
        assertTrue(Files.exists(disableMk1), "missing $disableMk1")

        val routingText = Files.readString(routing)
        assertTrue(routingText.contains("LAN_MK1"), "mangle must use LAN_MK1")
        assertTrue(routingText.contains("toTarazona"), "must define toTarazona")
        assertTrue(routingText.contains("8.243.126.161"), "SNAT target must be 8.243.126.161")
        assertTrue(routingText.contains("WAN-VLAN SFP-SFPPLUS1"), "WAN iface name for MK2")
        assertTrue(
            routingText.contains("38.224.231.4") && routingText.contains("NO se modifica"),
            "script must document that main IP .4 is untouched"
        )
        assertFalse(
            routingText.contains("remove address=38.224.231.4") ||
                routingText.contains("set [find address~\"38.224.231.4\"]"),
            "must not modify main IP 38.224.231.4"
        )

        val listText = Files.readString(addressList)
        assertTrue(listText.contains("Clientes con paginas problematicas"))
        assertTrue(listText.contains("192.168.30.202"), "VLAN100 client must be in list")
        assertFalse(listText.contains("address=0.0.0.0"), "invalid 0.0.0.0 must not be migrated")

        val disableText = Files.readString(disableMk1)
        assertTrue(disableText.contains("disabled=yes"))
        assertTrue(disableText.contains("8.243.126"))
        assertTrue(disableText.contains("toTarazona"))
    }
}
