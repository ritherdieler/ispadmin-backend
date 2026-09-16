package com.dscorp.wispadmin.wispadmin.config

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class LocalPrestagingStaticIpTrafficScriptTest {

    private val root: Path = Path.of(System.getProperty("user.dir"))
    private val scriptPath: Path = root.resolve("scripts/prestaging-static-ip-traffic-lab.sh")

    private fun script(): String {
        assertTrue(Files.exists(scriptPath), "missing $scriptPath")
        return Files.readString(scriptPath)
    }

    @Test
    fun script_targets_prestaging_mk2_wireless_static_ip() {
        val script = script()
        assertTrue(script.contains("http://127.0.0.1:8082/ispadmin"), script)
        assertTrue(script.contains("ispadmin_prestaging"), script)
        assertTrue(script.contains("HOST_DEVICE_ID=\"\${HOST_DEVICE_ID:-8}\""), script)
        assertTrue(script.contains("WIRELESS"), script)
        assertTrue(script.contains("STATIC_IP"), script)
        assertTrue(script.contains("/api/traffic/v1/admin/poll"), script)
        assertTrue(script.contains("/internal/traffic/targets"), script)
        assertTrue(script.contains("X-Traffic-Key"), script)
        assertTrue(script.contains("Authorization: Bearer"), script)
        assertTrue(script.contains("application-local-prestaging.secrets.properties"), script)
        assertTrue(script.contains("gigafiberperu.cloud"), script)
        assertFalse(script.contains("ispadmin_dev"), script)
        assertFalse(script.contains("HOST_DEVICE_ID=\"\${HOST_DEVICE_ID:-1}\""), script)
        assertFalse(script.contains("mikrotik_test"), script)
        assertFalse(script.contains("127.0.0.1:8091"), script)
    }

    @Test
    fun script_is_executable() {
        assertTrue(Files.isExecutable(scriptPath), "not executable: $scriptPath")
    }
}
