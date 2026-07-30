package com.dscorp.wispadmin.oltgateway.ssh

import com.dscorp.wispadmin.oltgateway.config.OltGatewayProperties
import com.dscorp.wispadmin.oltgateway.exception.OltUnreachableException
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class OltCliBusTest {

    private val properties = OltGatewayProperties().apply {
        session.poolSize = 4
        session.keepaliveEnabled = true
        session.keepaliveIntervalMs = 60_000
        commandTimeoutMs = 5_000
    }

    private lateinit var session: HuaweiCliSession
    private lateinit var bus: OltCliBus

    @BeforeEach
    fun setUp() {
        session = mockk(relaxed = true)
        every { session.execute(any()) } answers { "ok:${firstArg<String>()}" }
        every { session.ping() } returns 7L
        bus = OltCliBus(
            sshClient = mockk(relaxed = true),
            properties = properties,
            sessionFactory = { _, _ -> session }
        )
        bus.start()
    }

    @AfterEach
    fun tearDown() {
        bus.close()
    }

    @Test
    fun `effective session count is always 1`() {
        assertEquals(1, bus.sessionCount())
    }

    @Test
    fun `two concurrent submits never overlap on session`() {
        val inFlight = AtomicInteger(0)
        val maxInFlight = AtomicInteger(0)
        val firstEntered = CountDownLatch(1)
        val release = CountDownLatch(1)

        val first = bus.submit(CliJobType.ADHOC) {
            val current = inFlight.incrementAndGet()
            maxInFlight.updateAndGet { maxOf(it, current) }
            firstEntered.countDown()
            release.await(2, TimeUnit.SECONDS)
            inFlight.decrementAndGet()
            "a"
        }
        assertTrue(firstEntered.await(2, TimeUnit.SECONDS))

        val second = bus.submit(CliJobType.ADHOC) {
            val current = inFlight.incrementAndGet()
            maxInFlight.updateAndGet { maxOf(it, current) }
            inFlight.decrementAndGet()
            "b"
        }

        Thread.sleep(80)
        assertEquals(1, inFlight.get())
        assertEquals(1, maxInFlight.get())

        release.countDown()
        assertEquals(CliBusResult.Ok("a"), first.get(2, TimeUnit.SECONDS))
        assertEquals(CliBusResult.Ok("b"), second.get(2, TimeUnit.SECONDS))
        assertEquals(1, maxInFlight.get())
    }

    @Test
    fun `duplicate inventory while running is skipped as already_running`() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)

        val running = bus.submit(CliJobType.INVENTORY) {
            entered.countDown()
            release.await(2, TimeUnit.SECONDS)
            "inventory"
        }
        assertTrue(entered.await(2, TimeUnit.SECONDS))

        val skipped = bus.execute(CliJobType.INVENTORY) { "should-not-run" }
        assertEquals(CliBusResult.Skipped("already_running"), skipped)

        release.countDown()
        assertEquals(CliBusResult.Ok("inventory"), running.get(2, TimeUnit.SECONDS))
    }

    @Test
    fun `duplicate inventory while queued is skipped as already_queued`() {
        val hold = CountDownLatch(1)
        val adhocStarted = CountDownLatch(1)

        bus.submit(CliJobType.ADHOC) {
            adhocStarted.countDown()
            hold.await(2, TimeUnit.SECONDS)
            "adhoc"
        }
        assertTrue(adhocStarted.await(2, TimeUnit.SECONDS))

        val queued = bus.submit(CliJobType.INVENTORY) { "first-inventory" }
        val skipped = bus.execute(CliJobType.INVENTORY) { "second-inventory" }

        assertEquals(CliBusResult.Skipped("already_queued"), skipped)
        hold.countDown()
        assertEquals(CliBusResult.Ok("first-inventory"), queued.get(2, TimeUnit.SECONDS))
    }

    @Test
    fun `duplicate signal poll while running is skipped as already_running`() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)

        val running = bus.submit(CliJobType.SIGNAL_POLL) {
            entered.countDown()
            release.await(2, TimeUnit.SECONDS)
            "signal"
        }
        assertTrue(entered.await(2, TimeUnit.SECONDS))

        val skipped = bus.execute(CliJobType.SIGNAL_POLL) { "nope" }
        assertEquals(CliBusResult.Skipped("already_running"), skipped)

        release.countDown()
        assertEquals(CliBusResult.Ok("signal"), running.get(2, TimeUnit.SECONDS))
    }

    @Test
    fun `write runs before queued inventory`() {
        val order = mutableListOf<String>()
        val hold = CountDownLatch(1)
        val blockerStarted = CountDownLatch(1)

        bus.submit(CliJobType.KEEPALIVE) {
            blockerStarted.countDown()
            hold.await(2, TimeUnit.SECONDS)
            order += "blocker"
            "blocker"
        }
        assertTrue(blockerStarted.await(2, TimeUnit.SECONDS))

        val inventory = bus.submit(CliJobType.INVENTORY) {
            order += "inventory"
            "inventory"
        }
        val write = bus.submit(CliJobType.WRITE) {
            order += "write"
            "write"
        }

        hold.countDown()
        assertEquals(CliBusResult.Ok("write"), write.get(2, TimeUnit.SECONDS))
        assertEquals(CliBusResult.Ok("inventory"), inventory.get(2, TimeUnit.SECONDS))
        assertEquals(listOf("blocker", "write", "inventory"), order)
    }

    @Test
    fun `adhoc has higher priority than keepalive`() {
        val order = mutableListOf<String>()
        val hold = CountDownLatch(1)
        val blockerStarted = CountDownLatch(1)

        bus.submit(CliJobType.WRITE) {
            blockerStarted.countDown()
            hold.await(2, TimeUnit.SECONDS)
            order += "blocker"
            "blocker"
        }
        assertTrue(blockerStarted.await(2, TimeUnit.SECONDS))

        val keepalive = bus.submit(CliJobType.KEEPALIVE) {
            order += "keepalive"
            "keepalive"
        }
        val adhoc = bus.submit(CliJobType.ADHOC) {
            order += "adhoc"
            "adhoc"
        }

        hold.countDown()
        assertEquals(CliBusResult.Ok("adhoc"), adhoc.get(2, TimeUnit.SECONDS))
        assertEquals(CliBusResult.Ok("keepalive"), keepalive.get(2, TimeUnit.SECONDS))
        assertEquals(listOf("blocker", "adhoc", "keepalive"), order)
    }

    @Test
    fun `nested execute on worker thread is reentrant`() {
        val result = bus.execute(CliJobType.INVENTORY) { outer ->
            val nested = bus.execute(CliJobType.ADHOC) { inner ->
                inner.execute("nested")
            }
            (nested as CliBusResult.Ok).value + ":" + outer.execute("outer")
        }
        assertEquals(CliBusResult.Ok("ok:nested:ok:outer"), result)
        verify(exactly = 1) { session.execute("nested") }
        verify(exactly = 1) { session.execute("outer") }
    }

    @Test
    fun `ping uses session through bus`() {
        assertEquals(7L, bus.ping())
        verify(exactly = 1) { session.ping() }
    }

    @Test
    fun `execute propaga OltUnreachableException sin envolver en ExecutionException`() {
        every { session.execute(any<String>()) } throws OltUnreachableException("Unable to reach OLT at 10.11.104.2:22")

        val ex = assertThrows(OltUnreachableException::class.java) {
            bus.execute(CliJobType.INVENTORY) { session.execute("display board 0") }
        }
        assertEquals("Unable to reach OLT at 10.11.104.2:22", ex.message)
    }

    @Test
    fun `background jobs skip fast when OLT is degraded`() {
        val tracker = OltReachabilityTracker(failureThreshold = 1, backoffMs = 120_000) { 0L }
        tracker.recordFailure()
        val degradedBus = OltCliBus(
            sshClient = mockk(relaxed = true),
            properties = properties,
            sessionFactory = { _, _ -> session },
            reachability = tracker
        )
        degradedBus.start()
        try {
            val skipped = degradedBus.execute(CliJobType.INVENTORY) { "should-not-run" }
            assertEquals(CliBusResult.Skipped("olt_unreachable"), skipped)
            verify(exactly = 0) { session.execute(any()) }
        } finally {
            degradedBus.close()
        }
    }

    @Test
    fun `status exposes queue depth and busy job type`() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)

        bus.submit(CliJobType.WRITE) {
            entered.countDown()
            release.await(2, TimeUnit.SECONDS)
            "w"
        }
        assertTrue(entered.await(2, TimeUnit.SECONDS))
        assertEquals(CliJobType.WRITE, bus.busyJobType())

        bus.submit(CliJobType.ADHOC) { "a" }
        assertTrue(bus.queueDepth() >= 1)

        release.countDown()
        Thread.sleep(100)
        assertNull(bus.busyJobType())
        assertEquals(0, bus.queueDepth())
    }
}
