package com.dscorp.wispadmin.architecture

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class HttpClientCoreIsolationTest {
    @Test
    fun healthAndNetdiagHttpClientsDoNotImportCoreDomain() {
        val violations = listOf(
            "servicehealth/src/main/kotlin/com/dscorp/wispadmin/servicehealth/client",
            "netdiag/src/main/kotlin/com/dscorp/wispadmin/netdiag/client",
        ).flatMap { folder ->
            val dir = File(folder)
            if (!dir.exists()) return@flatMap emptyList()
            dir.walkTopDown().filter { it.extension == "kt" }.flatMap { file ->
                file.readLines()
                    .filter { it.startsWith("import com.dscorp.wispadmin.wispadmin.") }
                    .map { "${file.path}: $it" }
            }.toList()
        }
        assertTrue(violations.isEmpty(), violations.joinToString("\n"))
    }
}
