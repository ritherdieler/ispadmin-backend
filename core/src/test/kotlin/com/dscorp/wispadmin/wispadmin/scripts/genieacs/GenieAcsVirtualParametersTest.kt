package com.dscorp.wispadmin.wispadmin.scripts.genieacs

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class GenieAcsVirtualParametersTest {

    private val root: Path = Path.of(System.getProperty("user.dir"))
    private val vparams: Path = root.resolve("scripts/genieacs/virtual-parameters")

    private val names = listOf(
        "GfApplyInternetPppoe",
        "GfApplyInternetStatic",
        "GfSetWifi",
        "GfReboot",
        "GfInternetStatus",
        "GfWifiStatus",
        "GfPppoeUsername",
        "GfPppoePassword",
        "GfPppoeVlanId",
        "GfPppoeConnectionName",
    )

    private val files = listOf(
        "gf-apply-internet-pppoe.js",
        "gf-apply-internet-static.js",
        "gf-set-wifi.js",
        "gf-reboot.js",
        "gf-internet-status.js",
        "gf-wifi-status.js",
        "gf-pppoe-username.js",
        "gf-pppoe-password.js",
        "gf-pppoe-vlan-id.js",
        "gf-pppoe-connection-name.js",
    )

    @Test
    fun vparam_scripts_exist_for_each_intent() {
        files.forEach { name ->
            val path = vparams.resolve(name)
            assertTrue(Files.exists(path), "missing $path")
        }
        assertTrue(Files.exists(vparams.resolve("lib/gf-vparams-core.js")))
    }

    @Test
    fun nbi_push_puts_virtual_parameters_by_name() {
        val script = read("scripts/genieacs/apply-virtual-parameters-via-nbi.sh")
        assertTrue(script.contains("PUT"), script)
        assertTrue(script.contains("/virtual_parameters/"), script)
        names.forEach { name ->
            assertTrue(script.contains(name), "missing $name in NBI push: $script")
        }
        assertTrue(script.contains("gf-vparams-core.js"), script)
        assertFalse(script.contains("/provisions/Gf"), script)
    }

    @Test
    fun entry_scripts_do_not_dereference_bare_device_id() {
        files.forEach { name ->
            val src = read("scripts/genieacs/virtual-parameters/$name")
            assertTrue(
                src.contains("typeof _deviceId"),
                "$name must guard GenieACS vparam sandbox without _deviceId: $src",
            )
        }
    }

    @Test
    fun v2804_pppoe_script_targets_wcd2_not_management_wcd1() {
        val core = read("scripts/genieacs/virtual-parameters/lib/gf-vparams-core.js")
        assertTrue(core.contains("V2804AX15T"), core)
        assertTrue(core.contains("F6600R"), core)
        assertTrue(core.contains("WANConnectionDevice.2.WANPPPConnection.1"), core)
        assertTrue(core.contains("WANConnectionDevice.1.WANPPPConnection.2"), core)
        assertTrue(core.contains("{path: Date.now()}"), core)
        assertTrue(core.contains("WANPPPConnection."), core)
        assertTrue(core.contains("WLANConfiguration.5"), core)
        assertTrue(core.contains("WLANConfiguration.1"), core)
        assertTrue(core.contains("10.20."), core)
    }

    @Test
    fun pppoe_provision_maps_vsol_to_wcd2_and_f6600_to_ppp2() {
        val src = read("scripts/genieacs/provisions/gf-pppoe-wan2-poc.js")
        assertTrue(src.contains("V2804AX15T"), src)
        assertTrue(src.contains("VSOLVA74"), src)
        assertTrue(src.contains("F6600R"), src)
        assertTrue(src.contains("WANConnectionDevice.2.WANPPPConnection.1"), src)
        assertTrue(src.contains("WANConnectionDevice.2.WANIPConnection.1"), src)
        assertTrue(src.contains("WANConnectionDevice.1.WANPPPConnection.2"), src)
        assertTrue(src.contains("wcdPath"), src)
        assertTrue(src.contains("wcdParent"), src)
        assertTrue(src.contains("X_CT-COM_WANGponLinkConfig.VLANIDMark"), src)
        assertTrue(src.contains("unsupported productClass"), src)
        assertTrue(src.contains("function ensureInternetWcd"), src)
        assertTrue(src.contains("function deleteInternetIpWan"), src)
        assertTrue(src.contains("function ensureInternetPpp"), src)
        assertTrue(src.contains("function setInternetPppLeaves"), src)
        assertTrue(src.contains("function enableInternetPpp"), src)
        assertTrue(src.contains("function setWifi"), src)
        assertTrue(src.contains("WLANConfiguration.1"), src)
        assertTrue(src.contains("WLANConfiguration.5"), src)
        assertTrue(src.contains("KeyPassphrase"), src)
        assertTrue(src.contains("args[5]"), src)
        assertFalse(src.contains("11111111"), src)
    }

    @Test
    fun wifi_ssid_provision_maps_vsol_and_f6600_and_uses_one_passphrase() {
        val src = read("scripts/genieacs/provisions/gf-wifi-ssid-poc.js")
        assertTrue(src.contains("V2804AX15T"), src)
        assertTrue(src.contains("VSOLVA74"), src)
        assertTrue(src.contains("F6600R"), src)
        assertTrue(src.contains("WLANConfiguration.1"), src)
        assertTrue(src.contains("WLANConfiguration.5"), src)
        assertTrue(src.contains("KeyPassphrase"), src)
        assertTrue(src.contains("function setWifi"), src)
        assertTrue(src.contains("unsupported productClass"), src)
        assertFalse(src.contains("WANPPPConnection"), src)
        assertFalse(src.contains("WANIPConnection"), src)
    }

    @Test
    fun reboot_provision_declares_cwmp_reboot_only() {
        val src = read("scripts/genieacs/provisions/gf-reboot-poc.js")
        assertTrue(src.contains("\"Reboot\""), src)
        assertFalse(src.contains("Tags"), src)
        assertFalse(src.contains("WANPPPConnection"), src)
        assertFalse(src.contains("WLANConfiguration"), src)
    }

    private fun read(relative: String): String {
        val path = root.resolve(relative)
        assertTrue(Files.exists(path), "missing $path")
        return Files.readString(path)
    }
}
