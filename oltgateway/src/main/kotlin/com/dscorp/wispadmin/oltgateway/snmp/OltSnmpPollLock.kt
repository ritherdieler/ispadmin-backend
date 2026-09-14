package com.dscorp.wispadmin.oltgateway.snmp

import java.time.Duration
import java.util.UUID
import org.slf4j.LoggerFactory

interface OltSnmpPollLockStore {
    fun tryAcquire(key: String, ownerId: String, ttl: Duration): Boolean
    fun forceAcquire(key: String, ownerId: String, ttl: Duration)
    fun ttlRemaining(key: String): Long?
    fun release(key: String, ownerId: String)
}

interface OltSnmpPollLocker {
    fun <T> withLock(block: () -> T): T
}

class NoOpOltSnmpPollLock : OltSnmpPollLocker {
    override fun <T> withLock(block: () -> T): T = block()
}

class OltSnmpPollLock(
    private val store: OltSnmpPollLockStore,
    private val key: String,
    private val ttl: Duration,
    private val waitSlice: Duration,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val sleeper: (Long) -> Unit = { Thread.sleep(it) },
    private val ownerId: String = UUID.randomUUID().toString(),
) : OltSnmpPollLocker {

    companion object {
        private val logger = LoggerFactory.getLogger(OltSnmpPollLock::class.java)

        fun resolveKey(configured: String, namespace: String, shared: Boolean): String {
            if (shared || namespace.isBlank()) return configured
            return "$namespace:$configured"
        }
    }

    override fun <T> withLock(block: () -> T): T {
        acquireOrWait()
        try {
            return block()
        } finally {
            store.release(key, ownerId)
        }
    }

    private fun acquireOrWait() {
        if (store.tryAcquire(key, ownerId, ttl)) {
            return
        }
        val started = clock()
        val remaining = store.ttlRemaining(key)
        val maxWait = (remaining ?: ttl.toMillis()).coerceAtMost(ttl.toMillis()).coerceAtLeast(0L)
        val deadline = started + maxWait
        logger.info(
            "SNMP_OPTICAL_POLL_LOCK_WAIT key={} remainingMs={} waitSliceMs={}",
            key,
            remaining,
            waitSlice.toMillis()
        )
        while (true) {
            val now = clock()
            if (now >= deadline) {
                logger.warn("SNMP_OPTICAL_POLL_LOCK_TTL_EXPIRED key={} polling anyway", key)
                store.forceAcquire(key, ownerId, ttl)
                return
            }
            val slice = minOf(waitSlice.toMillis(), deadline - now).coerceAtLeast(1L)
            sleeper(slice)
            if (store.tryAcquire(key, ownerId, ttl)) {
                return
            }
        }
    }
}
