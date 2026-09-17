package com.dscorp.wispadmin.wispadmin.config

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class AssistanceTicketPriorityMigrationTest {

    @Test
    fun v45ConvertsPriorityFromVarcharToInt() {
        val sql = Files.readString(repoRoot().resolve("core/src/main/resources/db/migration/V45__assistance_ticket_priority_int.sql"))
        assertTrue(sql.contains("UPDATE assistance_ticket"), sql)
        assertTrue(Regex("""MODIFY COLUMN\s+priority\s+INT""", RegexOption.IGNORE_CASE).containsMatchIn(sql), sql)
    }

    private fun repoRoot(): Path {
        var dir = Path.of(System.getProperty("user.dir"))
        repeat(6) {
            if (Files.exists(dir.resolve("core/src/main/resources/db/migration"))) {
                return dir
            }
            dir = dir.parent ?: return Path.of(System.getProperty("user.dir"))
        }
        return Path.of(System.getProperty("user.dir"))
    }
}
