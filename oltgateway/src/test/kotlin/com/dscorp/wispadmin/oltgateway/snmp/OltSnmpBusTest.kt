package com.dscorp.wispadmin.oltgateway.snmp

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class OltSnmpBusTest {

    @Test
    fun `permits=1 serializa walks concurrentes`() {
        val bus = OltSnmpBus(oltId = "olt-a", maxConcurrent = 1, acquireTimeoutMs = 5_000)
        val overlap = AtomicInteger(0)
        val maxOverlap = AtomicInteger(0)
        val started = CountDownLatch(2)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val tasks = (1..2).map {
                executor.submit {
                    bus.acquire(SnmpJobType.OPTICAL) {
                        val now = overlap.incrementAndGet()
                        maxOverlap.updateAndGet { maxOf(it, now) }
                        started.countDown()
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

    @Test
    fun `permits=2 permite solape`() {
        val bus = OltSnmpBus(oltId = "olt-a", maxConcurrent = 2, acquireTimeoutMs = 5_000)
        val overlap = AtomicInteger(0)
        val maxOverlap = AtomicInteger(0)
        val bothInside = CountDownLatch(2)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val tasks = (1..2).map {
                executor.submit {
                    bus.acquire(SnmpJobType.INVENTORY) {
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
            assertTrue(maxOverlap.get() >= 2, "expected overlap, was ${maxOverlap.get()}")
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `timeout de acquire lanza IOException`() {
        val bus = OltSnmpBus(oltId = "olt-a", maxConcurrent = 1, acquireTimeoutMs = 50)
        val holding = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        try {
            executor.submit {
                bus.acquire(SnmpJobType.OPTICAL) {
                    holding.countDown()
                    Thread.sleep(400)
                    "hold"
                }
            }
            assertTrue(holding.await(2, TimeUnit.SECONDS))
            val ex = assertThrows(IOException::class.java) {
                bus.acquire(SnmpJobType.INVENTORY) { "should-not-run" }
            }
            assertTrue(ex.message!!.contains("snmp_bus_timeout"), ex.message)
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `inFlight sube y baja`() {
        val bus = OltSnmpBus(oltId = "olt-a", maxConcurrent = 1, acquireTimeoutMs = 1_000)
        assertEquals(0, bus.inFlight())
        bus.acquire(SnmpJobType.PROBE) {
            assertEquals(1, bus.inFlight())
            "x"
        }
        assertEquals(0, bus.inFlight())
    }
}
