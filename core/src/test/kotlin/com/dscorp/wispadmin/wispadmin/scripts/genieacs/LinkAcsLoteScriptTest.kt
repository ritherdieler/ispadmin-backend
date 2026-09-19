package com.dscorp.wispadmin.wispadmin.scripts.genieacs

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class LinkAcsLoteScriptTest {

    private val root: Path = Path.of(System.getProperty("user.dir"))

    @Test
    fun runner_posts_core_acs_link_and_dry_run_does_not_provision_wan() {
        val script = root.resolve("scripts/genieacs/link-acs-lote.sh")
        assertTrue(Files.exists(script), "missing $script")
        val text = Files.readString(script)
        assertTrue(text.contains("/subscription/acs/link"), text)
        assertTrue(text.contains("dryRun"), text)
        assertTrue(text.contains("/users/login"), text)
        assertTrue(text.contains("api.gigafiberperu.cloud/ispadmin"), text)
        assertTrue(text.contains("/tmp/vsol-tr069-inverted-todo.tsv"), text)
        assertTrue(text.contains("--list-ghosts"), text)
        assertTrue(text.contains("--delete-ghosts"), text)
        assertTrue(text.contains("/subscription/acs/ghosts"), text)
        assertFalse(text.contains("retry-tr069"), text)
        assertFalse(text.contains("gateway.provision"), text)
        assertFalse(text.contains("setParameterValues"), text)
        assertFalse(text.contains("/provisions/"), text)
        assertFalse(text.contains("/api/olt-gateway/"), text)
        assertFalse(text.contains("10.11.104.2"), text)
    }
}
