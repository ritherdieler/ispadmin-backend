package com.dscorp.wispadmin.traffic.config

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component

/**
 * Applies the narrow, idempotent schema repair required by bandwidth samples.
 *
 * The application currently relies on Hibernate ddl-auto=update rather than a
 * migration runner. Hibernate can add the new columns, but it does not relax
 * existing NOT NULL constraints, backfill enum values on legacy rows, or
 * remove indexes made redundant by a unique constraint.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class TrafficSchemaCompatibilityRunner(
    private val jdbcTemplate: JdbcTemplate,
    @Value("\${traffic.schema-compatibility.enabled:true}")
    private val enabled: Boolean,
) : ApplicationRunner {

    override fun run(args: ApplicationArguments) {
        if (!enabled || !tableExists("subscription_traffic_sample")) return
        removeRedundantSampleIndex()
        if (schemaIsMarkedCompatible()) return

        val nullableMetrics = listOf(
            "rx_bytes_delta" to "BIGINT",
            "tx_bytes_delta" to "BIGINT",
            "avg_mbps_down" to "DOUBLE",
            "avg_mbps_up" to "DOUBLE",
        )

        nullableMetrics.forEach { (column, sqlType) ->
            if (columnExists(column) && !columnIsNullable(column)) {
                jdbcTemplate.execute(
                    "ALTER TABLE subscription_traffic_sample MODIFY COLUMN $column $sqlType NULL",
                )
                log.info("Traffic schema compatibility: made {} nullable", column)
            }
        }

        if (columnExists("sample_status")) {
            val repaired = jdbcTemplate.update(
                """
                UPDATE subscription_traffic_sample
                SET sample_status = 'OK'
                WHERE sample_status IS NULL OR TRIM(sample_status) = ''
                """.trimIndent(),
            )
            if (repaired > 0) log.info("Traffic schema compatibility: repaired {} sample statuses", repaired)
            jdbcTemplate.execute(
                "ALTER TABLE subscription_traffic_sample MODIFY COLUMN sample_status VARCHAR(24) NOT NULL DEFAULT 'OK' COMMENT 'bandwidth-v1-compatible'",
            )
        }

        if (columnExists("collected_at")) {
            jdbcTemplate.update(
                "UPDATE subscription_traffic_sample SET collected_at = bucket_start WHERE collected_at IS NULL",
            )
        }
        if (columnExists("interval_seconds")) {
            jdbcTemplate.update(
                "UPDATE subscription_traffic_sample SET interval_seconds = 300 WHERE interval_seconds IS NULL AND sample_status = 'OK'",
            )
        }
    }

    private fun tableExists(table: String): Boolean = jdbcTemplate.queryForObject(
        """
        SELECT COUNT(*)
        FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?
        """.trimIndent(),
        Int::class.java,
        table,
    ) != 0

    private fun columnExists(column: String): Boolean = jdbcTemplate.queryForObject(
        """
        SELECT COUNT(*)
        FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'subscription_traffic_sample'
          AND COLUMN_NAME = ?
        """.trimIndent(),
        Int::class.java,
        column,
    ) != 0

    private fun removeRedundantSampleIndex() {
        if (indexExists("uk_traffic_sample_sub_bucket") && indexExists("idx_traffic_sample_sub_bucket")) {
            jdbcTemplate.execute("ALTER TABLE subscription_traffic_sample DROP INDEX idx_traffic_sample_sub_bucket")
            log.info("Traffic schema compatibility: removed redundant subscription/bucket index")
        }
    }

    private fun indexExists(index: String): Boolean = jdbcTemplate.queryForObject(
        """
        SELECT COUNT(*)
        FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'subscription_traffic_sample'
          AND INDEX_NAME = ?
        """.trimIndent(),
        Int::class.java,
        index,
    ) != 0

    private fun columnIsNullable(column: String): Boolean = jdbcTemplate.queryForObject(
        """
        SELECT IS_NULLABLE
        FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'subscription_traffic_sample'
          AND COLUMN_NAME = ?
        """.trimIndent(),
        String::class.java,
        column,
    ) == "YES"

    private fun schemaIsMarkedCompatible(): Boolean = jdbcTemplate.queryForObject(
        """
        SELECT COLUMN_COMMENT
        FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'subscription_traffic_sample'
          AND COLUMN_NAME = 'sample_status'
        """.trimIndent(),
        String::class.java,
    ) == "bandwidth-v1-compatible"

    private companion object {
        val log = LoggerFactory.getLogger(TrafficSchemaCompatibilityRunner::class.java)
    }
}
