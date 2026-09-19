package com.dscorp.wispadmin.wispadmin.scripts.genieacs

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class RetagTr069Vlan1000ScriptTest {

    private val root: Path = Path.of(System.getProperty("user.dir"))

    @Test
    fun runner_ensures_olt_vlan1000_via_core_before_genieacs_provision() {
        val script = root.resolve("scripts/genieacs/retag-tr069-vlan1000.sh")
        assertTrue(Files.exists(script), "missing $script")
        val text = Files.readString(script)
        val oltIdx = text.indexOf("service-port/ensure-mgmt")
        val provisionIdx = text.indexOf("\"/provisions/gf-tr069-vlan1000\"")
        assertTrue(oltIdx >= 0, text)
        assertTrue(text.contains("/onu/"), text)
        assertTrue(text.contains("/users/login"), text)
        assertTrue(text.contains("CORE_BASE"), text)
        assertTrue(text.contains("127.0.0.1:8082/ispadmin"), text)
        assertTrue(text.contains("VSOL0031C0B6"), text)
        assertTrue(text.contains("aborting CPE retag"), text)
        assertTrue(provisionIdx >= 0, text)
        assertTrue(oltIdx < provisionIdx, "OLT ensure-mgmt must run before NBI PUT: $text")
        assertFalse(text.contains("/api/olt-gateway/"), text)
        assertFalse(text.contains("10.11.104.2"), "must not open a second OLT SSH: $text")
    }
}
