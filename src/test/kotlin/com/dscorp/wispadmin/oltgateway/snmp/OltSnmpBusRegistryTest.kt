package com.dscorp.wispadmin.oltgateway.snmp

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class OltSnmpBusRegistryTest {

    @Test
    fun `misma olt reusa el mismo bus`() {
        val registry = OltSnmpBusRegistry(acquireTimeoutMs = 1_000)
        val a = registry.forOlt("gigafiber-ma5608t", "MA5608T")
        val b = registry.forOlt("gigafiber-ma5608t", "MA5608T")
        assertSame(a, b)
        assertEquals(1, a.maxConcurrent)
    }

    @Test
    fun `olts distintas tienen buses independientes`() {
        val registry = OltSnmpBusRegistry(acquireTimeoutMs = 1_000)
        val a = registry.forOlt("olt-a", "MA5608T")
        val b = registry.forOlt("olt-b", "MA5608T")
        assertNotSame(a, b)
    }

    @Test
    fun `el limite del bus sale del modelo no de un global`() {
        val registry = OltSnmpBusRegistry(
            acquireTimeoutMs = 1_000,
            maxWalksForModel = { code -> if (code == "FAT-OLT") 3 else 1 }
        )
        assertEquals(1, registry.forOlt("ma", "MA5608T").maxConcurrent)
        assertEquals(3, registry.forOlt("fat", "FAT-OLT").maxConcurrent)
    }

    @Test
    fun `walks de dos OLTs no se bloquean entre si`() {
        val registry = OltSnmpBusRegistry(acquireTimeoutMs = 5_000)
        val overlap = AtomicInteger(0)
        val maxOverlap = AtomicInteger(0)
        val bothInside = CountDownLatch(2)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val tasks = listOf("olt-a", "olt-b").map { oltId ->
                executor.submit {
                    registry.forOlt(oltId, "MA5608T").acquire(SnmpJobType.OPTICAL) {
                        overlap.incrementAndGet()
                        maxOverlap.updateAndGet { maxOf(it, overlap.get()) }
                        bothInside.countDown()
                        bothInside.await(2, TimeUnit.SECONDS)
                        overlap.decrementAndGet()
                        "ok"
                    }
                }
            }
            tasks.forEach { it.get(5, TimeUnit.SECONDS) }
            assertTrue(maxOverlap.get() >= 2, "expected cross-OLT overlap, was ${maxOverlap.get()}")
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `misma OLT MA5608T sigue serializando`() {
        val registry = OltSnmpBusRegistry(acquireTimeoutMs = 5_000)
        val overlap = AtomicInteger(0)
        val maxOverlap = AtomicInteger(0)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val tasks = (1..2).map {
                executor.submit {
                    registry.forOlt("gigafiber-ma5608t", "MA5608T").acquire(SnmpJobType.OPTICAL) {
                        val now = overlap.incrementAndGet()
                        maxOverlap.updateAndGet { maxOf(it, now) }
                        Thread.sleep(80)
                        overlap.decrementAndGet()
                        "ok"
                    }
                }
            }
            tasks.forEach { it.get(5, TimeUnit.SECONDS) }
            assertEquals(1, maxOverlap.get())
        } finally {
            executor.shutdownNow()
        }
    }
}
