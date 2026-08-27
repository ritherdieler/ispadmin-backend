package com.dscorp.wispadmin.oltgateway.snmp

import java.util.ArrayDeque

/**
 * In-memory ring of recent OLT SNMP traps for ops dump before NetDiag mapping exists.
 */
class RecentOltSnmpTrapBuffer(
    private val capacity: Int
) {
    private val lock = Any()
    private val items = ArrayDeque<OltSnmpTrapEvent>()

    fun accept(event: OltSnmpTrapEvent) {
        synchronized(lock) {
            items.addFirst(event)
            while (items.size > capacity.coerceAtLeast(1)) {
                items.removeLast()
            }
        }
    }

    fun recent(limit: Int = capacity): List<OltSnmpTrapEvent> {
        synchronized(lock) {
            return items.take(limit.coerceAtLeast(0))
        }
    }

    fun size(): Int = synchronized(lock) { items.size }
}
