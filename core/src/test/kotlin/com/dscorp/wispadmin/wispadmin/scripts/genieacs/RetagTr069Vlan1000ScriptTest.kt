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

    @Test
    fun runner_pings_internet_wan_before_and_after_and_aborts_if_lost() {
        val script = root.resolve("scripts/genieacs/retag-tr069-vlan1000.sh")
        val helper = root.resolve("scripts/genieacs/retag_internet_check.py")
        assertTrue(Files.exists(script), "missing $script")
        assertTrue(Files.exists(helper), "missing $helper")
        val text = Files.readString(script)
        val helperText = Files.readString(helper)
        val beforeIdx = text.indexOf("INTERNET_BEFORE")
        val ensureIdx = text.indexOf("service-port/ensure-mgmt")
        val afterEnsureIdx = text.indexOf("INTERNET_AFTER_OLT")
        val provisionIdx = text.indexOf("\"/provisions/gf-tr069-vlan1000\"")
        val afterCpeIdx = text.indexOf("INTERNET_AFTER_CPE")
        assertTrue(text.contains("retag_internet_check.py"), text)
        assertTrue(text.contains("api.gigafiberperu.cloud/ispadmin"), text)
        assertTrue(beforeIdx >= 0, text)
        assertTrue(afterEnsureIdx >= 0, text)
        assertTrue(afterCpeIdx >= 0, text)
        assertTrue(beforeIdx < ensureIdx, "baseline ping must run before ensure-mgmt: $text")
        assertTrue(afterEnsureIdx < provisionIdx, "OLT ping must run before NBI PUT: $text")
        assertTrue(afterCpeIdx > provisionIdx, "CPE ping must run after NBI enqueue: $text")
        assertTrue(helperText.contains("internet was reachable and became unreachable"), helperText)
        assertTrue(helperText.contains("192.168.252.0"), helperText)
        assertTrue(helperText.contains("10.20.0.0"), helperText)
        assertTrue(helperText.contains("classify_wans"), helperText)
        val selfTest = ProcessBuilder("python3", helper.toAbsolutePath().toString(), "--self-test")
            .directory(root.toFile())
            .start()
        assertTrue(selfTest.waitFor() == 0, String(selfTest.inputStream.readBytes() + selfTest.errorStream.readBytes()))
    }
}
