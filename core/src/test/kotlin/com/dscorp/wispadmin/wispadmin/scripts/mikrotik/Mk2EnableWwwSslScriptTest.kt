package com.dscorp.wispadmin.wispadmin.scripts.mikrotik

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class Mk2EnableWwwSslScriptTest {

    @Test
    fun mk2_www_ssl_scripts_exist() {
        val root = Path.of(System.getProperty("user.dir"))
        val enable = root.resolve("scripts/mk2-enable-www-ssl.py")
        val export = root.resolve("scripts/mk2-export-truststore.sh")
        assertTrue(Files.exists(enable), "missing $enable")
        assertTrue(Files.isExecutable(export), "not executable: $export")
        val text = Files.readString(enable)
        assertTrue(text.contains("netdiag-rest-mk2"))
        assertTrue(text.contains("212.85.13.47/32"))
    }
}
