package com.dscorp.wispadmin.wispadmin.service.cleanup

data class TraceForeignKey(
    val childTable: String,
    val childColumn: String,
    val parentTable: String,
    val parentColumn: String,
)

interface SubscriptionTraceCatalog {
    fun tablesWithSubscriptionId(): List<String>
    fun foreignKeys(): List<TraceForeignKey>
    fun primaryKey(table: String): String
    fun idsWhere(table: String, column: String, value: String): List<String>
    fun deleteWhere(table: String, column: String, value: String)
    fun deleteWhereIn(table: String, column: String, values: List<String>)
    fun stripJsonId(subscriptionId: Int)
    fun countWhere(table: String, column: String, value: String): Int
}

class SubscriptionTraceEraserEngine(
    private val catalog: SubscriptionTraceCatalog,
    private val journalTable: String = JOURNAL_TABLE,
) : SubscriptionTraceEraser {
    override fun erase(snapshot: CleanupSnapshot) {
        val subscriptionId = snapshot.subscriptionId.toString()
        val direct = catalog.tablesWithSubscriptionId().filter { it != journalTable && it != SUBSCRIPTION }.toSet()
        val keys = catalog.foreignKeys()
        val involved = (direct + SUBSCRIPTION + descendants(direct + SUBSCRIPTION, keys)).filter { it != journalTable }.toSet()
        for (table in childrenFirst(involved, keys)) {
            when {
                table == SUBSCRIPTION -> catalog.deleteWhere(SUBSCRIPTION, "id", subscriptionId)
                table in direct -> catalog.deleteWhere(table, "subscription_id", subscriptionId)
                else -> deleteByParent(table, keys, direct, subscriptionId)
            }
        }
        catalog.stripJsonId(snapshot.subscriptionId)
        val leftover = (direct + SUBSCRIPTION).firstOrNull { table ->
            val column = if (table == SUBSCRIPTION) "id" else "subscription_id"
            catalog.countWhere(table, column, subscriptionId) > 0
        }
        if (leftover != null) {
            throw CleanupStepException(CleanupFailure(
                code = "TRACE_REMAINING",
                message = "Quedó un registro de la suscripción. Puede reintentar.",
                detail = leftover,
                retryable = true,
            ))
        }
    }

    private fun deleteByParent(
        table: String,
        keys: List<TraceForeignKey>,
        direct: Set<String>,
        subscriptionId: String,
    ) {
        keys.filter { it.childTable == table }.forEach { fk ->
            val parentIds = when {
                fk.parentTable == SUBSCRIPTION -> listOf(subscriptionId)
                fk.parentTable in direct -> catalog.idsWhere(fk.parentTable, "subscription_id", subscriptionId)
                else -> emptyList()
            }
            if (parentIds.isNotEmpty()) catalog.deleteWhereIn(table, fk.childColumn, parentIds)
        }
    }

    private fun descendants(roots: Set<String>, keys: List<TraceForeignKey>): Set<String> {
        val found = roots.toMutableSet()
        var grew = true
        while (grew) {
            grew = false
            keys.filter { it.parentTable in found && it.childTable !in found }.forEach {
                found += it.childTable
                grew = true
            }
        }
        return found
    }

    private fun childrenFirst(tables: Set<String>, keys: List<TraceForeignKey>): List<String> {
        val remaining = tables.toMutableSet()
        val ordered = mutableListOf<String>()
        while (remaining.isNotEmpty()) {
            val leaves = remaining.filter { table ->
                keys.none { it.parentTable == table && it.childTable in remaining && it.childTable != table }
            }
            val next = leaves.ifEmpty { listOf(remaining.first()) }
            ordered += next
            remaining -= next.toSet()
        }
        return ordered
    }

    companion object {
        const val JOURNAL_TABLE = "subscription_hard_cleanup"
        const val SUBSCRIPTION = "subscription"
    }
}
