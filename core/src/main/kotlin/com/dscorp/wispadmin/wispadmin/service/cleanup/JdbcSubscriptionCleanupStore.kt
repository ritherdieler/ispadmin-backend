package com.dscorp.wispadmin.wispadmin.service.cleanup

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import java.sql.ResultSet

@Component
class JdbcCleanupJournal(
    private val jdbc: JdbcTemplate,
    private val json: ObjectMapper,
) : CleanupJournal {
    override fun load(subscriptionId: Int): CleanupRun {
        val rows = jdbc.query(
            "SELECT snapshot_json, steps_json FROM subscription_hard_cleanup WHERE subscription_id = ?",
            { rs, _ -> read(rs) },
            subscriptionId,
        )
        return rows.firstOrNull() ?: throw NoSuchElementException("CLEANUP_NOT_FOUND")
    }

    override fun save(updated: CleanupRun) {
        val snapshot = json.writeValueAsString(updated.snapshot)
        val steps = json.writeValueAsString(updated.steps)
        val updatedRows = jdbc.update(
            "UPDATE subscription_hard_cleanup SET snapshot_json = ?, steps_json = ?, updated_at = CURRENT_TIMESTAMP WHERE subscription_id = ?",
            snapshot,
            steps,
            updated.snapshot.subscriptionId,
        )
        if (updatedRows == 0) {
            jdbc.update(
                "INSERT INTO subscription_hard_cleanup (subscription_id, snapshot_json, steps_json) VALUES (?,?,?)",
                updated.snapshot.subscriptionId,
                snapshot,
                steps,
            )
        }
    }

    override fun drop(subscriptionId: Int) {
        jdbc.update("DELETE FROM subscription_hard_cleanup WHERE subscription_id = ?", subscriptionId)
    }

    private fun read(rs: ResultSet): CleanupRun = CleanupRun(
        snapshot = json.readValue(rs.getString("snapshot_json")),
        steps = json.readValue(rs.getString("steps_json")),
    )
}

@Component
class JdbcSubscriptionTraceEraser(
    catalog: JdbcSubscriptionTraceCatalog,
) : SubscriptionTraceEraser {
    private val engine = SubscriptionTraceEraserEngine(catalog)

    override fun erase(snapshot: CleanupSnapshot) = engine.erase(snapshot)
}

@Component
class JdbcSubscriptionTraceCatalog(
    private val jdbc: JdbcTemplate,
) : SubscriptionTraceCatalog {
    override fun tablesWithSubscriptionId(): List<String> = names(
        "SELECT TABLE_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND COLUMN_NAME = 'subscription_id'",
    )

    override fun foreignKeys(): List<TraceForeignKey> = jdbc.query(
        """
        SELECT TABLE_NAME, COLUMN_NAME, REFERENCED_TABLE_NAME, REFERENCED_COLUMN_NAME
        FROM information_schema.KEY_COLUMN_USAGE
        WHERE TABLE_SCHEMA = DATABASE() AND REFERENCED_TABLE_NAME IS NOT NULL
        """.trimIndent(),
    ) { rs, _ ->
        TraceForeignKey(
            childTable = rs.getString("TABLE_NAME"),
            childColumn = rs.getString("COLUMN_NAME"),
            parentTable = rs.getString("REFERENCED_TABLE_NAME"),
            parentColumn = rs.getString("REFERENCED_COLUMN_NAME"),
        )
    }.filter { safe(it.childTable) && safe(it.childColumn) && safe(it.parentTable) && safe(it.parentColumn) }

    override fun primaryKey(table: String): String {
        require(safe(table))
        return jdbc.queryForList(
            """
            SELECT COLUMN_NAME FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND INDEX_NAME = 'PRIMARY'
            ORDER BY SEQ_IN_INDEX
            """.trimIndent(),
            String::class.java,
            table,
        ).firstOrNull()?.takeIf { safe(it) } ?: "id"
    }

    override fun idsWhere(table: String, column: String, value: String): List<String> {
        require(safe(table) && safe(column))
        val pk = primaryKey(table)
        return jdbc.queryForList("SELECT `$pk` FROM `$table` WHERE `$column` = ?", String::class.java, value)
    }

    override fun deleteWhere(table: String, column: String, value: String) {
        require(safe(table) && safe(column))
        jdbc.update("DELETE FROM `$table` WHERE `$column` = ?", value)
    }

    override fun deleteWhereIn(table: String, column: String, values: List<String>) {
        if (values.isEmpty()) return
        require(safe(table) && safe(column))
        val marks = values.joinToString(",") { "?" }
        jdbc.update("DELETE FROM `$table` WHERE `$column` IN ($marks)", *values.toTypedArray())
    }

    override fun stripJsonId(subscriptionId: Int) {
        val columns = jdbc.query(
            """
            SELECT TABLE_NAME, COLUMN_NAME FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE() AND COLUMN_NAME = 'subscription_ids_json'
            """.trimIndent(),
        ) { rs, _ -> rs.getString("TABLE_NAME") to rs.getString("COLUMN_NAME") }
        columns.filter { safe(it.first) && safe(it.second) }.forEach { (table, column) ->
            val pk = primaryKey(table)
            val rows = jdbc.query(
                "SELECT `$pk` AS pk, `$column` AS payload FROM `$table` WHERE `$column` LIKE ?",
                { rs, _ -> rs.getString("pk") to rs.getString("payload") },
                "%$subscriptionId%",
            )
            rows.forEach row@{ (pkValue, payload) ->
                val ids = runCatching { jsonIds(payload) }.getOrElse { return@row }
                if (subscriptionId !in ids) return@row
                val left = ids.filter { it != subscriptionId }
                if (left.isEmpty()) jdbc.update("DELETE FROM `$table` WHERE `$pk` = ?", pkValue)
                else jdbc.update(
                    "UPDATE `$table` SET `$column` = ? WHERE `$pk` = ?",
                    json.writeValueAsString(left),
                    pkValue,
                )
            }
        }
    }

    override fun countWhere(table: String, column: String, value: String): Int {
        require(safe(table) && safe(column))
        return jdbc.queryForObject("SELECT COUNT(*) FROM `$table` WHERE `$column` = ?", Int::class.java, value) ?: 0
    }

    private fun names(sql: String): List<String> =
        jdbc.queryForList(sql, String::class.java).filter { safe(it) }

    private fun jsonIds(payload: String): List<Int> {
        val node = json.readTree(payload)
        if (!node.isArray) return emptyList()
        return node.mapNotNull { item -> if (item.isNumber) item.asInt().takeIf { it > 0 } else null }
    }

    private fun safe(name: String) = SAFE.matches(name)

    private companion object {
        val SAFE = Regex("[A-Za-z0-9_]+")
        val json = com.fasterxml.jackson.databind.ObjectMapper()
    }
}
