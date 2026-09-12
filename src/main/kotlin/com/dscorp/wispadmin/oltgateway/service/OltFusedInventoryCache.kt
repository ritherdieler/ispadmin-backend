package com.dscorp.wispadmin.oltgateway.service

import com.dscorp.wispadmin.oltgateway.parser.ParsedOnuSummary
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference

/**
 * Hands the inventory half of the fused per-port SNMP pass from the signal poll to the
 * inventory sync. Consume-once: `persistSnapshot` soft-deletes ONUs missing from the snapshot,
 * so the sync only persists a fused snapshot whose SN set covers every active DB ONU
 * (authorize-after-capture, skipped port, new board); otherwise it falls back to a live walk.
 */
class OltFusedInventoryCache(private val now: () -> Instant = Instant::now) {

    data class Snapshot(val onus: List<ParsedOnuSummary>, val capturedAt: Instant)

    private val ref = AtomicReference<Snapshot?>(null)

    fun publish(onus: List<ParsedOnuSummary>) {
        if (onus.isEmpty()) return
        ref.set(Snapshot(onus, now()))
    }

    fun takeIfFresh(maxAgeMs: Long): Snapshot? {
        val current = ref.get() ?: return null
        val ageMs = now().toEpochMilli() - current.capturedAt.toEpochMilli()
        if (ageMs > maxAgeMs) {
            ref.compareAndSet(current, null)
            return null
        }
        return if (ref.compareAndSet(current, null)) current else null
    }

    fun peek(): Snapshot? = ref.get()
}
