package com.dscorp.wispadmin.oltgateway.service

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class OmciManagementV2Test {
    private val target = OmciManagementTarget("HWTC9F4BF950", 1, 6, 39, 2)
    private fun info(serial: String = target.serial) = """
        F/S/P : 0/1/6
        ONT-ID : 39
        SN : 485754439F4BF950 ($serial)
        Run state : online
        Config state : normal
        Match state : match
        TR069 management : Enable
        TR069 IP index : 0
        TR069 server profile ID : 2
    """.trimIndent()
    private val ip = """
        ONT IP host index : 0
        ONT config type : DHCP
        ONT manage VLAN : 1000
        ONT manage priority : 2
        ONT IP : 10.20.1.166
    """.trimIndent()

    @Test fun `extra IP hosts do not hide management on index 0`() {
        val mixed = """
            ONT IP host index : 0
            ONT config type : DHCP
            ONT manage VLAN : 1000
            ONT manage priority : 2
            ONT IP : 10.20.1.166
            ONT IP host index : 1
            ONT config type : PPPoE
            ONT manage VLAN : 100
            ONT manage priority : 0
            ONT IP : 10.64.1.8
        """.trimIndent()
        val service = OmciManagementV2 { run -> run { command -> when {
            command.startsWith("display ont info") -> info()
            command.startsWith("display ont ipconfig") -> mixed
            else -> ""
        } } }
        assertEquals(OmciManagementEvidence(true, "10.20.1.166"), service.ensure(target))
    }

    @Test fun `matching management is read only and returns DHCP evidence`() {
        val commands = mutableListOf<String>()
        val service = OmciManagementV2 { run -> run { command -> commands += command; when {
            command.startsWith("display ont info") -> info()
            command.startsWith("display ont ipconfig") -> ip
            else -> ""
        } } }
        assertEquals(OmciManagementEvidence(true, "10.20.1.166"), service.ensure(target))
        assertFalse(commands.any { it.startsWith("ont ") })
        assertEquals("quit", commands.last())
    }

    @Test fun `wrong serial and mismatch fail before mutation`() {
        for (text in listOf(info("ZTEGDC47BFFD"), info().replace("Match state : match", "Match state : mismatch"))) {
            val writes = mutableListOf<String>()
            val service = OmciManagementV2 { run -> run { command -> if (command.startsWith("ont ")) writes += command; text } }
            assertThrows(IllegalStateException::class.java) { service.ensure(target) }
            assertTrue(writes.isEmpty())
        }
    }

    @Test fun `creates management only inside GPON and reads back the result`() {
        val commands = mutableListOf<String>()
        var configured = false
        val service = OmciManagementV2 { run -> run { command -> commands += command; when {
            command.startsWith("display ont info") -> info().replace("profile ID : 2", "profile ID : ${if (configured) "2" else "-"}")
            command.startsWith("display ont ipconfig") -> if (configured) ip else "Failure: The ONT does not configure IP information"
            command.startsWith("ont tr069-server-config") -> { configured = true; "" }
            else -> ""
        } } }
        assertTrue(service.ensure(target).configured)
        assertTrue(commands.contains("ont ipconfig 6 39 ip-index 0 dhcp vlan 1000 priority 2"))
        assertTrue(commands.contains("ont tr069-server-config 6 39 profile-id 2"))
        assertEquals("interface gpon 0/1", commands.first())
        assertEquals("quit", commands.last())
    }

    @Test fun `CLI failure stops the sequence and is not mistaken for success`() {
        val service = OmciManagementV2 { run -> run { command -> when {
            command.startsWith("display ont info") -> info()
            command.startsWith("display ont ipconfig") -> "ONT IP host index : 0\nONT config type : Invalid"
            command.startsWith("ont ipconfig") -> "% Unknown command"
            else -> ""
        } } }
        assertThrows(IllegalStateException::class.java) { service.ensure(target) }
    }
}
