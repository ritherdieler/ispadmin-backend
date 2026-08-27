package com.dscorp.wispadmin.oltgateway.snmp

import org.slf4j.LoggerFactory
import java.io.IOException
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

enum class SnmpJobType {
    PROBE,
    INVENTORY,
    AUTOFIND,
    OPTICAL
}

/**
 * Limits concurrent SNMP GET/GETBULK against one OLT agent (UDP :161).
 * Capacity comes from [OltSnmpModelLimits] for that OLT's model — not a process-wide cap.
 */
class OltSnmpBus(
    val oltId: String,
    val maxConcurrent: Int,
    private val acquireTimeoutMs: Long
) {
    companion object {
        private val logger = LoggerFactory.getLogger(OltSnmpBus::class.java)
    }

    private val permits = maxConcurrent.coerceAtLeast(1)
    private val semaphore = Semaphore(permits, true)
    private val inFlightCount = AtomicInteger(0)

    fun inFlight(): Int = inFlightCount.get()

    fun availablePermits(): Int = semaphore.availablePermits()

    fun <T> acquire(type: SnmpJobType, block: () -> T): T {
        val timeout = acquireTimeoutMs.coerceAtLeast(1L)
        logger.debug(
            "SNMP bus acquire olt={} type={} inFlight={} available={} max={}",
            oltId,
            type,
            inFlightCount.get(),
            semaphore.availablePermits(),
            permits
        )
        val got = try {
            semaphore.tryAcquire(timeout, TimeUnit.MILLISECONDS)
        } catch (ex: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IOException("SNMP bus interrupted waiting for permit olt=$oltId type=$type", ex)
        }
        if (!got) {
            throw IOException(
                "snmp_bus_timeout after ${timeout}ms olt=$oltId type=$type inFlight=${inFlightCount.get()} max=$permits"
            )
        }
        inFlightCount.incrementAndGet()
        try {
            return block()
        } finally {
            inFlightCount.decrementAndGet()
            semaphore.release()
        }
    }
}
