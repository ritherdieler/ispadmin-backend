package com.dscorp.wispadmin.wispadmin.scripts.snmp

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class OpticalMaxRepAbScriptTest {

    @Test
    fun script_walks_port_1_6_with_lock_and_no_three_lane() {
        val root = Path.of(System.getProperty("user.dir"))
        val script = root.resolve("scripts/snmp/optical-maxrep-ab.sh")
        assertTrue(Files.exists(script), "missing $script")
        val text = Files.readString(script)
        assertTrue(text.contains("port 1/6"), text)
        assertTrue(text.contains("maxRep 15 vs 10"), text)
        assertTrue(text.contains("timeout 15s"), text)
        assertTrue(text.contains("olt-snmp-poll"), text)
        assertTrue(text.contains("parallel=1"), text)
        assertTrue(text.contains("OpticalMaxRepAbLiveSmokeTest"), text)
        assertFalse(text.contains("optical-info all"), text)
    }
}
