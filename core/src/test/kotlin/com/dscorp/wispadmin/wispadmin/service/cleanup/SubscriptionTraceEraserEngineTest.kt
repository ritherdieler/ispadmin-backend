package com.dscorp.wispadmin.wispadmin.service.cleanup

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class SubscriptionTraceEraserEngineTest {
    @Test
    fun `borra hijas, la suscripcion y el id dentro de un json`() {
        val catalog = MemoryTraceCatalog()
        catalog.row("assistance_ticket", mapOf("id" to "7", "subscription_id" to "42"))
        catalog.row("ticket_photo", mapOf("id" to "1", "ticket_id" to "7"))
        catalog.row("payment", mapOf("id" to "3", "subscription_id" to "42"))
        catalog.row("payment", mapOf("id" to "4", "subscription_id" to "99"))
        catalog.row("subscription", mapOf("id" to "42"))
        catalog.row("subscription_hard_cleanup", mapOf("subscription_id" to "42"))
        catalog.json += "42" to mutableListOf(42, 99)

        SubscriptionTraceEraserEngine(catalog).erase(CleanupSnapshot(subscriptionId = 42))

        assertTrue(catalog.rows.none { it.table == "ticket_photo" })
        assertTrue(catalog.rows.none { it.table == "assistance_ticket" })
        assertEquals(listOf("99"), catalog.rows.filter { it.table == "payment" }.map { it.columns["subscription_id"] })
        assertTrue(catalog.rows.none { it.table == "subscription" && it.columns["id"] == "42" })
        assertEquals(listOf(99), catalog.json["42"])
        assertEquals(1, catalog.rows.count { it.table == "subscription_hard_cleanup" })
    }

    @Test
    fun `si queda una fila el detalle nombra la tabla`() {
        val catalog = MemoryTraceCatalog()
        catalog.row("payment", mapOf("id" to "3", "subscription_id" to "42"))
        catalog.keep("payment")

        val error = assertThrows<CleanupStepException> {
            SubscriptionTraceEraserEngine(catalog).erase(CleanupSnapshot(subscriptionId = 42))
        }

        assertEquals("TRACE_REMAINING", error.failure.code)
        assertEquals("payment", error.failure.detail)
    }
}

private class MemoryTraceCatalog : SubscriptionTraceCatalog {
    val rows = mutableListOf<MemoryRow>()
    val json = mutableMapOf<String, MutableList<Int>>()
    private val sticky = mutableSetOf<String>()

    fun row(table: String, columns: Map<String, String>) {
        rows += MemoryRow(table, columns.toMutableMap())
    }

    fun keep(table: String) {
        sticky += table
    }

    override fun tablesWithSubscriptionId(): List<String> =
        rows.filter { "subscription_id" in it.columns }.map { it.table }.distinct()

    override fun foreignKeys(): List<TraceForeignKey> = listOf(
        TraceForeignKey("ticket_photo", "ticket_id", "assistance_ticket", "id"),
        TraceForeignKey("assistance_ticket", "subscription_id", "subscription", "id"),
        TraceForeignKey("payment", "subscription_id", "subscription", "id"),
    )

    override fun primaryKey(table: String): String = "id"

    override fun idsWhere(table: String, column: String, value: String): List<String> =
        rows.filter { it.table == table && it.columns[column] == value }.mapNotNull { it.columns["id"] }

    override fun deleteWhere(table: String, column: String, value: String) {
        if (table in sticky) return
        rows.removeAll { it.table == table && it.columns[column] == value }
    }

    override fun deleteWhereIn(table: String, column: String, values: List<String>) {
        rows.removeAll { it.table == table && it.columns[column] in values }
    }

    override fun stripJsonId(subscriptionId: Int) {
        json.values.forEach { ids -> ids.remove(subscriptionId) }
    }

    override fun countWhere(table: String, column: String, value: String): Int =
        rows.count { it.table == table && it.columns[column] == value }
}

private data class MemoryRow(val table: String, val columns: MutableMap<String, String>)
