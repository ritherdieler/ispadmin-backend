package com.dscorp.wispadmin.wispadmin.scripts.mikrotik

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class Mk2ReconcileQueuesScriptTest {

    @Test
    fun mk2_reconcile_scripts_exist() {
        val root = Path.of(System.getProperty("user.dir"))
        val cli = root.resolve("scripts/mk2-reconcile-queues.mjs")
        val lib = root.resolve("scripts/mk2-reconcile-queues-lib.mjs")
        val sql = root.resolve("scripts/sql/mk2-regularize-host-device.sql")
        val runbook = root.resolve(".agent-docs/mk2-queue-reconcile-2026-08-28.md")
        assertTrue(Files.exists(cli), "missing $cli")
        assertTrue(Files.exists(lib), "missing $lib")
        assertTrue(Files.exists(sql), "missing $sql")
        assertTrue(Files.exists(runbook), "missing $runbook")
        val libText = Files.readString(lib)
        assertTrue(libText.contains("id:%d, usuario:%s %s, lugar:%s, nap:%s, plan:%s, tipo:%s"))
        val cliText = Files.readString(cli)
        assertTrue(cliText.contains("/rest/queue/simple/print"))
        assertTrue(cliText.contains("--dry-run"))
    }
}
