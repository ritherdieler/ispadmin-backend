package com.dscorp.wispadmin.wispadmin.scripts.olt

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class VpsOltWireGuardScriptsTest {

    @Test
    fun wireguard_scripts_exist_and_setup_is_executable() {
        val root = Path.of(System.getProperty("user.dir"))
        val setup = root.resolve("scripts/setup-vps-olt-wg.sh")
        val apply = root.resolve("scripts/apply-mk2-olt-vps-wg-from-vps.sh")
        assertTrue(Files.exists(setup), "missing $setup")
        assertTrue(Files.isExecutable(setup), "not executable: $setup")
        assertTrue(Files.exists(apply), "missing $apply")
        assertTrue(Files.isExecutable(apply), "not executable: $apply")
        val unit = root.resolve("scripts/vps-olt-wg.service")
        val rsc = root.resolve("scripts/mikrotik-mk2-olt-vps-wg.rsc")
        assertTrue(Files.exists(unit), "missing $unit")
        assertTrue(Files.exists(rsc), "missing $rsc")
        val setupText = Files.readString(setup)
        assertTrue(setupText.contains("wg-quick"), "setup should use wg-quick")
    }
}
