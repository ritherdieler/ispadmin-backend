package com.dscorp.wispadmin.oltgateway.snmp

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger

class OltSnmpPollLockTest {

    @Test
    fun `acquire then release in finally`() {
        val store = FakeOltSnmpPollLockStore()
        val lock = OltSnmpPollLock(
            store = store,
            key = "olt-snmp-poll",
            ttl = Duration.ofMillis(20_000),
            waitSlice = Duration.ofMillis(1_000),
            clock = { store.nowMs },
            sleeper = { store.nowMs += it },
            ownerId = "owner-a",
        )
        var heldDuringBlock = false
        val result = lock.withLock {
            heldDuringBlock = store.isHeldBy("olt-snmp-poll", "owner-a")
            "ok"
        }
        assertTrue(heldDuringBlock)
        assertEquals("ok", result)
        assertFalse(store.isHeld("olt-snmp-poll"))
    }

    @Test
    fun `waits wait-slice until lock is free then acquires`() {
        val store = FakeOltSnmpPollLockStore()
        store.tryAcquire("olt-snmp-poll", "other", Duration.ofMillis(5_000))
        val sleeps = AtomicInteger(0)
        val lock = OltSnmpPollLock(
            store = store,
            key = "olt-snmp-poll",
            ttl = Duration.ofMillis(20_000),
            waitSlice = Duration.ofMillis(1_000),
            clock = { store.nowMs },
            sleeper = { ms ->
                sleeps.incrementAndGet()
                store.nowMs += ms
                if (store.nowMs >= 2_000) {
                    store.release("olt-snmp-poll", "other")
                }
            },
            ownerId = "owner-a",
        )
        val result = lock.withLock { "polled" }
        assertEquals("polled", result)
        assertTrue(sleeps.get() >= 1)
        assertFalse(store.isHeld("olt-snmp-poll"))
    }

    @Test
    fun `after TTL without release polls anyway`() {
        val store = FakeOltSnmpPollLockStore()
        store.tryAcquire("olt-snmp-poll", "other", Duration.ofMillis(50_000))
        val lock = OltSnmpPollLock(
            store = store,
            key = "olt-snmp-poll",
            ttl = Duration.ofMillis(3_000),
            waitSlice = Duration.ofMillis(1_000),
            clock = { store.nowMs },
            sleeper = { store.nowMs += it },
            ownerId = "owner-a",
        )
        var ran = false
        lock.withLock { ran = true }
        assertTrue(ran)
        assertTrue(store.forceAcquireCount >= 1)
        assertFalse(store.isHeldBy("olt-snmp-poll", "other"))
        assertFalse(store.isHeld("olt-snmp-poll"))
    }

    @Test
    fun `release in finally even when block throws`() {
        val store = FakeOltSnmpPollLockStore()
        val lock = OltSnmpPollLock(
            store = store,
            key = "olt-snmp-poll",
            ttl = Duration.ofMillis(20_000),
            waitSlice = Duration.ofMillis(1_000),
            clock = { store.nowMs },
            sleeper = { store.nowMs += it },
            ownerId = "owner-a",
        )
        try {
            lock.withLock<Unit> { error("boom") }
        } catch (_: IllegalStateException) {
        }
        assertFalse(store.isHeld("olt-snmp-poll"))
    }

    @Test
    fun `shared lock key ignores redis namespace`() {
        assertEquals(
            "olt-snmp-poll",
            OltSnmpPollLock.resolveKey("olt-snmp-poll", namespace = "stg", shared = true),
        )
        assertEquals(
            "olt-snmp-poll",
            OltSnmpPollLock.resolveKey("olt-snmp-poll", namespace = "prod", shared = true),
        )
    }

    @Test
    fun `non-shared lock key uses redis namespace`() {
        assertEquals(
            "stg:olt-snmp-poll",
            OltSnmpPollLock.resolveKey("olt-snmp-poll", namespace = "stg", shared = false),
        )
    }
}

class FakeOltSnmpPollLockStore : OltSnmpPollLockStore {
    var nowMs: Long = 0
    var forceAcquireCount: Int = 0
    private val entries = mutableMapOf<String, Pair<String, Long>>()

    override fun tryAcquire(key: String, ownerId: String, ttl: Duration): Boolean {
        prune()
        if (entries.containsKey(key)) return false
        entries[key] = ownerId to (nowMs + ttl.toMillis())
        return true
    }

    override fun forceAcquire(key: String, ownerId: String, ttl: Duration) {
        forceAcquireCount++
        entries[key] = ownerId to (nowMs + ttl.toMillis())
    }

    override fun ttlRemaining(key: String): Long? {
        prune()
        val expireAt = entries[key]?.second ?: return null
        return (expireAt - nowMs).coerceAtLeast(0)
    }

    override fun release(key: String, ownerId: String) {
        val current = entries[key] ?: return
        if (current.first == ownerId) {
            entries.remove(key)
        }
    }

    fun isHeld(key: String): Boolean {
        prune()
        return entries.containsKey(key)
    }

    fun isHeldBy(key: String, ownerId: String): Boolean {
        prune()
        return entries[key]?.first == ownerId
    }

    private fun prune() {
        val expired = entries.filterValues { it.second <= nowMs }.keys
        expired.forEach { entries.remove(it) }
    }
}
