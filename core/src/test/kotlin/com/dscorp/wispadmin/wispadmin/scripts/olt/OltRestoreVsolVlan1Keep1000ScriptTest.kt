package com.dscorp.wispadmin.wispadmin.scripts.olt

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class OltRestoreVsolVlan1Keep1000ScriptTest {

    private val root: Path = Path.of(System.getProperty("user.dir"))

    @Test
    fun restore_script_maps_vlan1_and_vlan1000_only_on_the_customer_ont() {
        val script = root.resolve("scripts/olt-restore-vsol-vlan1-keep1000.expect")
        assertTrue(Files.exists(script), "missing $script")
        val text = Files.readString(script)
        assertTrue(text.contains("VSOL00872649"), text)
        assertTrue(text.contains("set port 1"), text)
        assertTrue(text.contains("set ontId 91"), text)
        assertTrue(text.contains("set lpId 13"), text)
        assertTrue(text.contains("undo service-port port 0/\$board/\$port ont \$ontId"), text)
        assertTrue(text.contains("ont modify \$port \$ontId ont-lineprofile-id \$lpId"), text)
        assertTrue(text.contains("service-port vlan 1 gpon 0/\$board/\$port ont \$ontId gemport 1"), text)
        assertTrue(text.contains("service-port vlan 1000 gpon 0/\$board/\$port ont \$ontId gemport 1"), text)
        val undoAt = text.indexOf("undo service-port port 0/\$board/\$port ont \$ontId")
        val modifyAt = text.indexOf("ont modify \$port \$ontId ont-lineprofile-id \$lpId")
        assertTrue(undoAt >= 0 && modifyAt > undoAt, text)
        assertTrue(text.contains("OLT_GATEWAY_PASSWORD") || text.contains("OLT_PASSWORD"), text)
        assertFalse(text.contains("ont-lineprofile gpon profile-id \$lpId profile-name"), text)
        assertFalse(text.contains("gem add"), text)
        assertFalse(text.contains("gem mapping"), text)
        assertFalse(text.contains("ont-lineprofile gpon profile-id 3 profile-name"), text)
        assertFalse(text.contains("ont-lineprofile gpon profile-id 12 profile-name"), text)
        assertFalse(text.contains("SetParameterValues"), text)
    }
}
