package com.dscorp.wispadmin.oltgateway.config

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class OltGatewaySchemaMigrateScriptTest {

    @Test
    fun staging_script_copies_olt_mgr_tables() {
        val sql = Files.readString(
            Path.of(System.getProperty("user.dir")).resolve("scripts/sql/migrate-olt-mgr-to-stg_oltgateway.sql")
        )
        assertTrue(sql.contains("CREATE DATABASE IF NOT EXISTS stg_oltgateway"), sql.take(400))
        assertTrue(sql.contains("olt_mgr_onu"), sql)
        assertTrue(sql.contains("olt_mgr_olt"), sql)
        assertTrue(sql.contains("olt_mgr_onu_status_current"), sql)
        assertTrue(sql.contains("ispadmin_staging"), sql)
        assertTrue(sql.contains("Do not run twice") || sql.contains("explicit confirmation"), sql)
    }
}
