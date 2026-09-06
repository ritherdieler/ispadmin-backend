package com.dscorp.wispadmin.wispadmin.config

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class FiberOnuSnFkDecoupleMigrationTest {

    private val root = Path.of(System.getProperty("user.dir"))

    @Test
    fun migrationDropsAnyFiberOnuSnForeignKeyByColumnNotFixedName() {
        val sql = Files.readString(
            root.resolve("src/main/resources/db/migration/V49__drop_hibernate_fiber_onu_sn_fk.sql")
        )
        assertTrue(
            sql.contains("COLUMN_NAME = 'fiber_onu_sn'"),
            "must locate FK by column fiber_onu_sn (Hibernate auto names differ from fk_subscription_fiber_onu)"
        )
        assertTrue(
            sql.contains("REFERENCED_TABLE_NAME = 'onu'"),
            "must only drop FKs that reference onu"
        )
        assertTrue(
            sql.contains("DROP FOREIGN KEY"),
            "must drop the leftover Hibernate FK"
        )
    }
}
