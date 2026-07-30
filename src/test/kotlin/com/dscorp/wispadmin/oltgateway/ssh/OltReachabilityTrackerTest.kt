package com.dscorp.wispadmin.oltgateway.ssh

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OltReachabilityTrackerTest {

    @Test
    fun `starts healthy and allows background jobs`() {
        val tracker = OltReachabilityTracker(failureThreshold = 2, backoffMs = 60_000) { 0L }

        assertFalse(tracker.isDegraded())
        assertFalse(tracker.shouldSkip(CliJobType.INVENTORY))
        assertFalse(tracker.shouldSkip(CliJobType.KEEPALIVE))
    }

    @Test
    fun `enters degraded after threshold failures and skips background jobs`() {
        var now = 0L
        val tracker = OltReachabilityTracker(failureThreshold = 2, backoffMs = 60_000) { now }

        tracker.recordFailure()
        assertFalse(tracker.isDegraded())

        tracker.recordFailure()
        assertTrue(tracker.isDegraded())
        assertTrue(tracker.shouldSkip(CliJobType.INVENTORY))
        assertTrue(tracker.shouldSkip(CliJobType.SIGNAL_POLL))
        assertTrue(tracker.shouldSkip(CliJobType.KEEPALIVE))
        assertFalse(tracker.shouldSkip(CliJobType.WRITE))
        assertEquals("olt_unreachable", tracker.skipReason())
    }

    @Test
    fun `recovers after backoff and success clears degraded state`() {
        var now = 0L
        val tracker = OltReachabilityTracker(failureThreshold = 1, backoffMs = 60_000) { now }

        tracker.recordFailure()
        assertTrue(tracker.isDegraded())

        now = 60_001L
        assertFalse(tracker.isDegraded())
        assertFalse(tracker.shouldSkip(CliJobType.KEEPALIVE))

        tracker.recordSuccess()
        tracker.recordFailure()
        assertTrue(tracker.isDegraded())
    }
}
