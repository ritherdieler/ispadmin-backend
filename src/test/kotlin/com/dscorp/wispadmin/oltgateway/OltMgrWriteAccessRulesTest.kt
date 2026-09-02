package com.dscorp.wispadmin.oltgateway

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class OltMgrWriteAccessRulesTest {

    @Test
    fun `OltMgr repositories are only imported inside oltgateway`() {
        val root = File("src/main/kotlin")
        val offenders = root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filterNot { it.path.contains("${File.separator}oltgateway${File.separator}") }
            .flatMap { file ->
                file.readLines()
                    .filter { line ->
                        line.contains("import com.dscorp.wispadmin.oltgateway.domain.repository.OltMgr")
                    }
                    .map { line -> "${file.path}: $line" }
            }
            .toList()

        assertTrue(offenders.isEmpty(), "OltMgr*Repository must stay inside oltgateway; offenders=" + offenders)
    }

    @Test
    fun `legacy wispadmin Onu entity is no longer a JPA entity`() {
        val onu = File("src/main/kotlin/com/dscorp/wispadmin/wispadmin/data/model/Onu.kt").readText()
        assertTrue(!onu.contains("@Entity"), "Onu must not be a JPA entity; inventory lives in olt_mgr_onu")
    }
}
