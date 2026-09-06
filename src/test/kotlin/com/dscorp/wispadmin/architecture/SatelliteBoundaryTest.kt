package com.dscorp.wispadmin.architecture

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class SatelliteBoundaryTest {
    @Test fun `deployable satellites do not import another application's code`() {
        val root = File("src/main/kotlin/com/dscorp/wispadmin")
        val violations = listOf("oltgateway", "traffic", "acs").flatMap { module ->
            val allowed = setOf(module, "events", "transport") + if (module == "traffic") setOf("routeros") else emptySet()
            File(root, module).walkTopDown().filter { it.extension == "kt" }.flatMap { file ->
                file.readLines().filter { it.startsWith("import com.dscorp.wispadmin.") }
                    .filter { it.removePrefix("import com.dscorp.wispadmin.").substringBefore('.') !in allowed }
                    .map { "${file.relativeTo(root)}: $it" }
            }.toList()
        }
        assertTrue(violations.isEmpty(), violations.joinToString("\n"))
    }
}
