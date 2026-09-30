package com.dscorp.wispadmin.wispadmin.schema

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.core.io.ClassPathResource
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator
import java.util.UUID

class ProvisioningV2CleanupMigrationTest {
    @Test
    fun `purges orphan rows and cascades provisioning children when an operation is deleted`() {
        val dataSource = DriverManagerDataSource(
            "jdbc:h2:mem:${UUID.randomUUID()};MODE=MySQL;DB_CLOSE_DELAY=-1",
            "sa",
            "",
        )
        val jdbc = JdbcTemplate(dataSource)
        jdbc.execute("CREATE TABLE provisioning_v2_operation (operation_id VARCHAR(64) PRIMARY KEY, environment VARCHAR(32) NOT NULL, subscription_id INT NULL)")
        jdbc.execute("CREATE TABLE provisioning_v2_operator_lock (operator_id BIGINT PRIMARY KEY, environment VARCHAR(32) NOT NULL, operation_id VARCHAR(64) NOT NULL UNIQUE, created_at_epoch_ms BIGINT NOT NULL)")
        jdbc.execute("CREATE TABLE provisioning_v2_event (id BIGINT AUTO_INCREMENT PRIMARY KEY, operation_id VARCHAR(64) NOT NULL, payload_json TEXT NOT NULL)")
        jdbc.execute("CREATE TABLE provisioning_v2_resource (operation_id VARCHAR(64) NOT NULL, resource_key VARCHAR(128) NOT NULL, snapshot_cipher TEXT NOT NULL, PRIMARY KEY (operation_id, resource_key))")

        jdbc.update("INSERT INTO provisioning_v2_operation (operation_id, environment, subscription_id) VALUES (?,?,?)", "kept-op", "staging", 42)
        jdbc.update("INSERT INTO provisioning_v2_operator_lock VALUES (?,?,?,?)", 71, "staging", "kept-op", 1L)
        jdbc.update("INSERT INTO provisioning_v2_operator_lock VALUES (?,?,?,?)", 72, "staging", "orphan-op", 2L)
        jdbc.update("INSERT INTO provisioning_v2_event (operation_id, payload_json) VALUES (?,?)", "kept-op", "{}")
        jdbc.update("INSERT INTO provisioning_v2_event (operation_id, payload_json) VALUES (?,?)", "orphan-op", "{}")
        jdbc.update("INSERT INTO provisioning_v2_resource VALUES (?,?,?)", "kept-op", "draft", "cipher")
        jdbc.update("INSERT INTO provisioning_v2_resource VALUES (?,?,?)", "orphan-op", "draft", "cipher")

        ResourceDatabasePopulator(
            ClassPathResource("db/migration/V61__provisioning_v2_cleanup_integrity.sql"),
        ).execute(dataSource)

        assertEquals(0, count(jdbc, "provisioning_v2_operator_lock", "orphan-op"))
        assertEquals(0, count(jdbc, "provisioning_v2_event", "orphan-op"))
        assertEquals(0, count(jdbc, "provisioning_v2_resource", "orphan-op"))

        jdbc.update("DELETE FROM provisioning_v2_operation WHERE operation_id = ?", "kept-op")

        assertEquals(0, count(jdbc, "provisioning_v2_operator_lock", "kept-op"))
        assertEquals(0, count(jdbc, "provisioning_v2_event", "kept-op"))
        assertEquals(0, count(jdbc, "provisioning_v2_resource", "kept-op"))
    }

    private fun count(jdbc: JdbcTemplate, table: String, operationId: String): Int =
        requireNotNull(jdbc.queryForObject("SELECT COUNT(*) FROM $table WHERE operation_id = ?", Int::class.java, operationId))
}
