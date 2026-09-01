package com.dscorp.wispadmin.wispadmin.config

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class StagingFiberE2eRunbookTest {

    @Test
    fun master_runbook_covers_seed_network_deploy_and_command() {
        val doc = Files.readString(
            Path.of(System.getProperty("user.dir"))
                .resolve(".agent-docs/staging-fiber-e2e-runbook.md"),
        )
        listOf(
            "e2e_register_fiber_staging_espresso.sh",
            "staging-e2e-seed-all.sh",
            "ZTEGDC47BFFD",
            "192.168.250.1/24",
            "dscorp",
            "lab-zte-e2e-24",
            "F6600R",
            "sfp-sfpplus2",
            "wg-olt",
            "tr069-e2e-hard-cleanup.sh",
            "deploy.sh --env staging",
            "oltgateway,servicehealth,netdiag,traffic",
            "tr069_e2e_firebase_delete.py",
            "emulator-5554",
        ).forEach { needle ->
            assertTrue(doc.contains(needle), "runbook missing $needle")
        }
    }
}
