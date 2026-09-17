package com.dscorp.wispadmin.wispadmin.config

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class ErrorLogTextMigrationTest {

    @Test
    fun v55ConvertsErrorLogErrorToText() {
        val sql = Files.readString(repoRoot().resolve("core/src/main/resources/db/migration/V55__error_log_error_text.sql"))
        assertTrue(Regex("""ALTER TABLE\s+error_log""", RegexOption.IGNORE_CASE).containsMatchIn(sql), sql)
        assertTrue(Regex("""MODIFY COLUMN\s+`?error`?\s+TEXT""", RegexOption.IGNORE_CASE).containsMatchIn(sql), sql)
        assertFalse(sql.contains("ispadmin_staging"), sql)
        assertFalse(Regex("""\bispadmin\.""", RegexOption.IGNORE_CASE).containsMatchIn(sql), sql)
    }

    @Test
    fun placeAreaUsesGeometryForMysqlValidate() {
        val src = Files.readString(
            repoRoot().resolve("core/src/main/kotlin/com/dscorp/wispadmin/wispadmin/data/model/Place.kt"),
        )
        assertTrue(Regex("""columnDefinition\s*=\s*"geometry"""", RegexOption.IGNORE_CASE).containsMatchIn(src), src)
        assertFalse(src.contains("POLYGON SRID 4326"), src)
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
