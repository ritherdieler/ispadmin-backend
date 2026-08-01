package com.dscorp.wispadmin.oltgateway.ssh

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class OltReachabilityTracker(
    private val failureThreshold: Int,
    private val backoffMs: Long,
    private val clock: () -> Long = { System.currentTimeMillis() }
) {

    private val consecutiveFailures = AtomicInteger(0)
    private val degradedUntilMs = AtomicLong(0L)

    fun isDegraded(): Boolean = clock() < degradedUntilMs.get()

    fun shouldSkip(type: CliJobType): Boolean {
        if (!isDegraded()) {
            return false
        }
        return when (type) {
            CliJobType.INVENTORY,
            CliJobType.SIGNAL_POLL,
            CliJobType.ALARM_POLL,
            CliJobType.KEEPALIVE -> true
            CliJobType.WRITE,
            CliJobType.ADHOC -> false
        }
    }

    fun skipReason(): String = "olt_unreachable"

    fun recordSuccess() {
        consecutiveFailures.set(0)
        degradedUntilMs.set(0L)
    }

    fun recordFailure() {
        val failures = consecutiveFailures.incrementAndGet()
        if (failures >= failureThreshold.coerceAtLeast(1)) {
            degradedUntilMs.set(clock() + backoffMs.coerceAtLeast(1_000))
        }
    }
}
