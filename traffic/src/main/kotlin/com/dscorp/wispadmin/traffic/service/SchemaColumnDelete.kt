package com.dscorp.wispadmin.traffic.service

import org.springframework.jdbc.core.JdbcTemplate

class SchemaColumnDelete(private val jdbc: JdbcTemplate) {
    fun delete(column: String, value: String) {
        require(SAFE.matches(column))
        val tables = jdbc.queryForList(
            "SELECT TABLE_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND COLUMN_NAME = ?",
            String::class.java,
            column,
        )
        tables.filter { SAFE.matches(it) }.forEach { table ->
            jdbc.update("DELETE FROM `$table` WHERE `$column` = ?", value)
        }
    }

    private companion object {
        val SAFE = Regex("[A-Za-z0-9_]+")
    }
}
