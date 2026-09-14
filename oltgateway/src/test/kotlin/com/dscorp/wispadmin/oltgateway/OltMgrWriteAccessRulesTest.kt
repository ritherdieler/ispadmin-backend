package com.dscorp.wispadmin.oltgateway

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isRegularFile
import kotlin.streams.asSequence

class OltMgrWriteAccessRulesTest {

    private val root = Path.of(System.getProperty("user.dir"))

    @Test
    fun `OltMgr repositories are only imported inside oltgateway`() {
        val modules = listOf(
            "shared", "events", "transport", "routeros", "servicehealth",
            "acs", "traffic", "core", "netdiag", "observability", "app",
        )
        val offenders = modules.flatMap { module ->
            val src = root.resolve("$module/src/main/kotlin")
            if (!Files.isDirectory(src)) return@flatMap emptyList()
            Files.walk(src).use { stream ->
                stream.asSequence()
                    .filter { it.isRegularFile() && it.toString().endsWith(".kt") }
                    .flatMap { file ->
                        Files.readAllLines(file).asSequence()
                            .filter { it.contains("import com.dscorp.wispadmin.oltgateway.domain.repository.OltMgr") }
                            .map { "${file}: $it" }
                    }
                    .toList()
            }
        }
        assertTrue(offenders.isEmpty(), "OltMgr*Repository must stay inside oltgateway; offenders=" + offenders)
    }

    @Test
    fun `legacy wispadmin Onu entity is no longer a JPA entity`() {
        val onu = Files.readString(
            root.resolve("core/src/main/kotlin/com/dscorp/wispadmin/wispadmin/data/model/Onu.kt"),
        )
        assertTrue(!onu.contains("@Entity"), "Onu must not be a JPA entity; inventory lives in olt_mgr_onu")
    }
}
