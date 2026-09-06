package com.dscorp.wispadmin.architecture

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class HttpClientCoreIsolationTest {
    @Test
    fun `health and netdiag HTTP clients do not import core domain`() {
        val root = File("src/main/kotlin/com/dscorp/wispadmin")
        val violations = listOf("servicehealth/client", "netdiag/client").flatMap { folder ->
            File(root, folder).walkTopDown().filter { it.extension == "kt" }.flatMap { file ->
                file.readLines()
                    .filter { it.startsWith("import com.dscorp.wispadmin.wispadmin.") }
                    .map { "${file.relativeTo(root)}: $it" }
            }.toList()
        }
        assertTrue(violations.isEmpty(), violations.joinToString("\n"))
    }
}
