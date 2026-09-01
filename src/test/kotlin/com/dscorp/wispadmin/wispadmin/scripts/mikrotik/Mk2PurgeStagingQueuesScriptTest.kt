package com.dscorp.wispadmin.wispadmin.scripts.mikrotik

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class Mk2PurgeStagingQueuesScriptTest {

    @Test
    fun purge_scripts_exist_and_require_tag() {
        val root = Path.of(System.getProperty("user.dir"))
        val cli = root.resolve("scripts/mk2-purge-staging-queues.mjs")
        val lib = root.resolve("scripts/mk2-purge-staging-queues-lib.mjs")
        val sql = root.resolve("scripts/sql/staging-ip-pool.sql")
        assertTrue(Files.exists(cli), "missing $cli")
        assertTrue(Files.exists(lib), "missing $lib")
        assertTrue(Files.exists(sql), "missing $sql")
        val libText = Files.readString(lib)
        assertTrue(libText.contains("selectPurgeCandidates"))
        assertTrue(libText.contains("env="))
        val sqlText = Files.readString(sql)
        assertTrue(sqlText.contains("192.168.250.1/24"))
        assertTrue(sqlText.contains("ispadmin_staging.ip_pool"))
    }
}
