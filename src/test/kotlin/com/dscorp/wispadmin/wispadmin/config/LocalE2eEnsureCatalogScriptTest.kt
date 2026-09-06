package com.dscorp.wispadmin.wispadmin.config

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class LocalE2eEnsureCatalogScriptTest {

    @Test
    fun ensure_script_upserts_dscorp_for_android_sha384_and_checks_fiber_catalog() {
        val script = Files.readString(root().resolve("scripts/local-e2e-ensure-catalog.sh"))
        assertTrue(script.contains("dscorp"), script)
        assertTrue(script.contains("nohacker"), script)
        assertTrue(script.contains("ADMIN"), script)
        assertTrue(script.contains("verified"), script)
        assertTrue(script.contains("sha384") || script.contains("SHA-384"), script)
        assertTrue(script.contains("pbkdf2"), script)
        assertTrue(script.contains("ispadmin_dev"), script)
        assertTrue(script.contains("9 de octubre"), script)
        assertTrue(script.contains("NO-001"), script)
        assertTrue(script.contains("FIBER"), script)
        assertTrue(script.contains("network_device"), script)
        assertTrue(script.contains("host_device_id = 8") || script.contains("host_device_id=8"), script)
        assertFalse(
            Regex("""password\\s*=\\s*['\"]123456['\"]""").containsMatchIn(script),
            "must not seed mockdata plaintext password 123456",
        )
        assertFalse(script.contains("labcore"), "local e2e uses prod credentials, not labcore")
        assertTrue(script.contains("ALCL12345678"), "must reject Core mock OLT serials")
        assertTrue(script.contains("ZTEGDC47BFFD"), script)
        assertTrue(script.contains("olt.service.mock.enabled"), script)
        assertTrue(script.contains("unconfigured_onus"), script)
    }

    private fun root(): Path = Path.of(System.getProperty("user.dir"))
}
