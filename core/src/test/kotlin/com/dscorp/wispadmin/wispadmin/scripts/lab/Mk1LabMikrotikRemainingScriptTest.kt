package com.dscorp.wispadmin.wispadmin.scripts.lab

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class Mk1LabMikrotikRemainingScriptTest {

    @Test
    fun remainingScript_exists_and_is_executable() {
        val root = Path.of(System.getProperty("user.dir"))
        val script = root.resolve("scripts/mk1-lab-mikrotik-remaining.sh")
        assertTrue(Files.exists(script), "missing ${script}")
        assertTrue(Files.isExecutable(script), "not executable: ${script}")
    }

    @Test
    fun seed_sql_includes_migrate_fixture_900003() {
        val root = Path.of(System.getProperty("user.dir"))
        val seed = Files.readString(root.resolve("scripts/lab/mk1-e2e-seed.sql"))
        assertTrue(seed.contains("900003"), "seed must define lab subscription 900003")
        assertTrue(seed.contains("192.168.250.3"), "seed must define migrate lab IP")
    }
}
