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
        assertTrue(text.contains("certifi"), text)
        assertTrue(text.contains("/devices/?query="), text)
        val selfTest = ProcessBuilder("python3", helper.toAbsolutePath().toString(), "--self-test")
            .directory(root.toFile())
            .start()
        assertTrue(selfTest.waitFor() == 0, String(selfTest.inputStream.readBytes() + selfTest.errorStream.readBytes()))
    }

    @Test
    fun runner_requires_cr_10_20_and_matching_olt_vendor() {
        val script = root.resolve("scripts/genieacs/retag-tr069-vlan1000.sh")
        val text = Files.readString(script)
        assertTrue(text.contains("--force"), text)
        assertTrue(text.contains("SN_VENDOR_MISMATCH"), text)
        assertTrue(text.contains("CR_OK"), text)
        assertTrue(text.contains("CR_STILL_OLD"), text)
        assertTrue(text.contains("CR_ALREADY_TARGET"), text)
        assertTrue(text.contains("SKIP_LAB"), text)
        assertTrue(text.contains("SKIP_STALE_INFORM"), text)
        assertTrue(text.contains("vendors_match"), text)
        assertTrue(text.contains("cr_is_target"), text)
        assertTrue(text.contains("ENQUEUE_RETRY"), text)
        assertTrue(text.indexOf("skip_reason") < text.indexOf("service-port/ensure-mgmt"), text)
        assertTrue(text.indexOf("CR_OK") > text.indexOf("INTERNET_AFTER_CPE"), text)
    }

    @Test
    fun runner_bind_mgmt_uses_tp_link_provision_and_acs_host_route() {
        val script = root.resolve("scripts/genieacs/retag-tr069-vlan1000.sh")
        val provision = root.resolve("scripts/genieacs/provisions/gf-tr069-bind-mgmt.js")
        assertTrue(Files.exists(script), "missing $script")
        assertTrue(Files.exists(provision), "missing $provision")
        val text = Files.readString(script)
        val provisionText = Files.readString(provision)
        assertTrue(text.contains("--bind-mgmt"), text)
        assertTrue(text.contains("gf-tr069-bind-mgmt"), text)
        assertTrue(text.contains("gethostbyname"), text)
        assertTrue(text.contains("BIND_MGMT"), text)
        assertTrue(provisionText.contains("X_TP_ServiceType"), provisionText)
        assertTrue(provisionText.contains("Layer3Forwarding"), provisionText)
        assertTrue(provisionText.contains("10.20.0.0"), provisionText)
        assertFalse(provisionText.contains("X_CT-COM_VLANIDMark"), provisionText)
    }
}
