package com.dscorp.wispadmin.traffic.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class SubscriptionTrafficLiveTickBuilderTest {

    @Test
    fun `findQueueRowForIp matches target without cidr`() {
        val queues = listOf(
            mapOf("target" to "10.0.0.5/32", "bytes" to "100/200", "rate" to "0/0")
        )
        val row = SubscriptionTrafficLiveTickBuilder.findQueueRowForIp(queues, "10.0.0.5")
        assertEquals("10.0.0.5/32", row?.get("target"))
    }

    @Test
    fun `buildFromQueueRow returns queueFound false when row missing`() {
        val result = SubscriptionTrafficLiveTickBuilder.buildFromQueueRow(
            subscriptionId = 1,
            queueRow = null,
            previous = SubscriptionTrafficLiveTickState()
        )
        assertFalse(result.tick.queueFound)
        assertEquals(0L, result.tick.rxBytesDelta)
        assertEquals(0L, result.tick.sessionRxBytes)
    }

    @Test
    fun `buildFromQueueRow accumulates session bytes from counter deltas`() {
        val first = SubscriptionTrafficLiveTickBuilder.buildFromQueueRow(
            subscriptionId = 1,
            queueRow = mapOf("target" to "10.0.0.5/32", "bytes" to "100/1000", "rate" to "0/0"),
            previous = SubscriptionTrafficLiveTickState(),
            timestamp = LocalDateTime.parse("2026-08-28T10:00:00")
        )
        assertEquals(0L, first.tick.rxBytesDelta)
        assertEquals(0L, first.tick.sessionRxBytes)

        val second = SubscriptionTrafficLiveTickBuilder.buildFromQueueRow(
            subscriptionId = 1,
            queueRow = mapOf("target" to "10.0.0.5/32", "bytes" to "150/2500", "rate" to "0/0"),
            previous = first.nextState,
            timestamp = LocalDateTime.parse("2026-08-28T10:00:01")
        )
        assertEquals(1500L, second.tick.rxBytesDelta)
        assertEquals(50L, second.tick.txBytesDelta)
        assertEquals(1500L, second.tick.sessionRxBytes)
        assertEquals(50L, second.tick.sessionTxBytes)
        assertTrue(second.tick.queueFound)
    }

    @Test
    fun `buildFromQueueRow uses rate when available`() {
        val result = SubscriptionTrafficLiveTickBuilder.buildFromQueueRow(
            subscriptionId = 1,
            queueRow = mapOf("target" to "10.0.0.5/32", "bytes" to "0/0", "rate" to "125000/250000"),
            previous = SubscriptionTrafficLiveTickState(lastRxBytes = 0L, lastTxBytes = 0L),
            timestamp = LocalDateTime.parse("2026-08-28T10:00:00")
        )
        assertEquals(1.0, result.tick.txMbps)
        assertEquals(2.0, result.tick.rxMbps)
    }

    @Test
    fun `buildFromQueueRow resets delta on counter rollback`() {
        val previous = SubscriptionTrafficLiveTickState(
            lastRxBytes = 5000L,
            lastTxBytes = 1000L,
            sessionRxBytes = 5000L,
            sessionTxBytes = 1000L
        )
        val result = SubscriptionTrafficLiveTickBuilder.buildFromQueueRow(
            subscriptionId = 1,
            queueRow = mapOf("target" to "10.0.0.5/32", "bytes" to "100/100", "rate" to "0/0"),
            previous = previous
        )
        assertEquals(0L, result.tick.rxBytesDelta)
        assertEquals(0L, result.tick.txBytesDelta)
        assertEquals(5000L, result.tick.sessionRxBytes)
        assertNull(result.tick.rxMbps?.takeIf { it > 0 })
    }
}
