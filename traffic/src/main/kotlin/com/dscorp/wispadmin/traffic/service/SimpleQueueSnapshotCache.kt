package com.dscorp.wispadmin.traffic.service

import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

@Component
class SimpleQueueSnapshotCache {
    internal var clock: Clock = Clock.systemUTC()
    internal var ttl: Duration = Duration.ofSeconds(2)

    private data class Entry(val rows: List<Map<String, String>>, val expiresAt: Instant)

    private val entries = ConcurrentHashMap<Int, Entry>()
    private val locks = ConcurrentHashMap<Int, Any>()

    fun getOrLoad(hostDeviceId: Int, loader: () -> List<Map<String, String>>): List<Map<String, String>> {
        val now = Instant.now(clock)
        val cached = entries[hostDeviceId]
        if (cached != null && now.isBefore(cached.expiresAt)) {
            return cached.rows
        }
        val lock = locks.computeIfAbsent(hostDeviceId) { Any() }
        synchronized(lock) {
            val again = Instant.now(clock)
            val current = entries[hostDeviceId]
            if (current != null && again.isBefore(current.expiresAt)) {
                return current.rows
            }
            val rows = loader()
            entries[hostDeviceId] = Entry(rows, again.plus(ttl))
            return rows
        }
    }
}
