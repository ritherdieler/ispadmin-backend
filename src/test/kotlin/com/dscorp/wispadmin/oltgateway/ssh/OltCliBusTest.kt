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
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class OltCliBusTest {

    private val properties = OltGatewayProperties().apply {
        session.poolSize = 2
        session.keepaliveEnabled = true
        session.keepaliveIntervalMs = 60_000
        commandTimeoutMs = 5_000
    }

    private val sessions = CopyOnWriteArrayList<HuaweiCliSession>()
    private lateinit var bus: OltCliBus

    private val interactiveSession: HuaweiCliSession get() = sessions[0]
    private val backgroundSession: HuaweiCliSession get() = sessions[1]

    private fun newSession(): HuaweiCliSession = mockk<HuaweiCliSession>(relaxed = true).also { mock ->
        every { mock.execute(any()) } answers { "ok:${firstArg<String>()}" }
        every { mock.ping() } returns 7L
    }

    private fun newBus(props: OltGatewayProperties = properties) = OltCliBus(
        sshClient = mockk(relaxed = true),
        properties = props,
        sessionFactory = { _, _ -> newSession().also { sessions += it } }
    )

    @BeforeEach
    fun setUp() {
        sessions.clear()
        bus = newBus()
        bus.start()
    }

    @AfterEach
    fun tearDown() {
        bus.close()
    }

    @Test
    fun `abre dos sesiones una por carril`() {
        assertEquals(2, bus.sessionCount())
        assertEquals(2, sessions.size)
    }

    @Test
    fun `con pool size 1 degrada a un solo carril`() {
        val single = OltGatewayProperties().apply {
            session.poolSize = 1
            session.keepaliveEnabled = false
            commandTimeoutMs = 5_000
        }
        val singleBus = newBus(single)
        singleBus.start()
        try {
            assertEquals(1, singleBus.sessionCount())
            assertEquals(CliBusResult.Ok("ok:x"), singleBus.execute(CliJobType.INVENTORY) { it.execute("x") })
        } finally {
            singleBus.close()
        }
    }

    @Test
    fun `una escritura corre mientras el fondo sigue ocupado`() {
        val backgroundEntered = CountDownLatch(1)
        val releaseBackground = CountDownLatch(1)

        val background = bus.submit(CliJobType.ALARM_POLL) {
            backgroundEntered.countDown()
            releaseBackground.await(3, TimeUnit.SECONDS)
            "alarms"
        }
        assertTrue(backgroundEntered.await(2, TimeUnit.SECONDS))

        val write = bus.submit(CliJobType.WRITE) { "write" }

        assertEquals(CliBusResult.Ok("write"), write.get(2, TimeUnit.SECONDS))
        assertEquals(CliJobType.ALARM_POLL, bus.busyJobType(CliLane.BACKGROUND))

        releaseBackground.countDown()
        assertEquals(CliBusResult.Ok("alarms"), background.get(3, TimeUnit.SECONDS))
    }

    @Test
    fun `el carril interactivo serializa sus propios trabajos`() {
        val inFlight = AtomicInteger(0)
        val maxInFlight = AtomicInteger(0)
        val firstEntered = CountDownLatch(1)
        val release = CountDownLatch(1)

        val first = bus.submit(CliJobType.WRITE) {
            maxInFlight.updateAndGet { maxOf(it, inFlight.incrementAndGet()) }
            firstEntered.countDown()
            release.await(2, TimeUnit.SECONDS)
            inFlight.decrementAndGet()
            "a"
        }
        assertTrue(firstEntered.await(2, TimeUnit.SECONDS))

        val second = bus.submit(CliJobType.ADHOC) {
            maxInFlight.updateAndGet { maxOf(it, inFlight.incrementAndGet()) }
            inFlight.decrementAndGet()
            "b"
        }

        Thread.sleep(80)
        assertEquals(1, inFlight.get())

        release.countDown()
        assertEquals(CliBusResult.Ok("a"), first.get(2, TimeUnit.SECONDS))
        assertEquals(CliBusResult.Ok("b"), second.get(2, TimeUnit.SECONDS))
        assertEquals(1, maxInFlight.get())
    }

    @Test
    fun `el carril de fondo serializa inventario senal y alarmas entre si`() {
        val inFlight = AtomicInteger(0)
        val maxInFlight = AtomicInteger(0)
        val firstEntered = CountDownLatch(1)
        val release = CountDownLatch(1)

        val inventory = bus.submit(CliJobType.INVENTORY) {
            maxInFlight.updateAndGet { maxOf(it, inFlight.incrementAndGet()) }
            firstEntered.countDown()
            release.await(2, TimeUnit.SECONDS)
            inFlight.decrementAndGet()
            "inventory"
        }
        assertTrue(firstEntered.await(2, TimeUnit.SECONDS))

        val signal = bus.submit(CliJobType.SIGNAL_POLL) {
            maxInFlight.updateAndGet { maxOf(it, inFlight.incrementAndGet()) }
            inFlight.decrementAndGet()
            "signal"
        }

        Thread.sleep(80)
        assertEquals(1, inFlight.get())

        release.countDown()
        assertEquals(CliBusResult.Ok("inventory"), inventory.get(2, TimeUnit.SECONDS))
        assertEquals(CliBusResult.Ok("signal"), signal.get(2, TimeUnit.SECONDS))
        assertEquals(1, maxInFlight.get())
    }

    @Test
    fun `cada carril usa su propia sesion`() {
        bus.execute(CliJobType.ADHOC) { it.execute("interactivo") }
        bus.execute(CliJobType.INVENTORY) { it.execute("fondo") }

        verify(exactly = 1) { interactiveSession.execute("interactivo") }
        verify(exactly = 0) { interactiveSession.execute("fondo") }
        verify(exactly = 1) { backgroundSession.execute("fondo") }
        verify(exactly = 0) { backgroundSession.execute("interactivo") }
    }

    @Test
    fun `un submit anidado reutiliza la sesion del carril en curso`() {
        val result = bus.execute(CliJobType.INVENTORY) { outer ->
            val nested = bus.execute(CliJobType.ADHOC) { inner -> inner.execute("nested") }
            (nested as CliBusResult.Ok).value + ":" + outer.execute("outer")
        }

        assertEquals(CliBusResult.Ok("ok:nested:ok:outer"), result)
        verify(exactly = 1) { backgroundSession.execute("nested") }
        verify(exactly = 1) { backgroundSession.execute("outer") }
        verify(exactly = 0) { interactiveSession.execute(any()) }
    }

    @Test
    fun `un submit anidado desde el carril interactivo no salta al de fondo`() {
        bus.execute(CliJobType.WRITE) { outer ->
            bus.execute(CliJobType.ALARM_POLL) { inner -> inner.execute("nested") }
            outer.execute("outer")
        }

        verify(exactly = 1) { interactiveSession.execute("nested") }
        verify(exactly = 1) { interactiveSession.execute("outer") }
        verify(exactly = 0) { backgroundSession.execute(any()) }
    }

    @Test
    fun `un inventario duplicado en curso se descarta como already_running`() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)

        val running = bus.submit(CliJobType.INVENTORY) {
            entered.countDown()
            release.await(2, TimeUnit.SECONDS)
            "inventory"
        }
        assertTrue(entered.await(2, TimeUnit.SECONDS))

        assertEquals(
            CliBusResult.Skipped("already_running"),
            bus.execute(CliJobType.INVENTORY) { "should-not-run" }
        )

        release.countDown()
        assertEquals(CliBusResult.Ok("inventory"), running.get(2, TimeUnit.SECONDS))
    }

    @Test
    fun `un inventario duplicado en cola se descarta como already_queued`() {
        val hold = CountDownLatch(1)
        val started = CountDownLatch(1)

        bus.submit(CliJobType.SIGNAL_POLL) {
            started.countDown()
            hold.await(2, TimeUnit.SECONDS)
            "signal"
        }
        assertTrue(started.await(2, TimeUnit.SECONDS))

        val queued = bus.submit(CliJobType.INVENTORY) { "first-inventory" }
        assertEquals(
            CliBusResult.Skipped("already_queued"),
            bus.execute(CliJobType.INVENTORY) { "second-inventory" }
        )

        hold.countDown()
        assertEquals(CliBusResult.Ok("first-inventory"), queued.get(2, TimeUnit.SECONDS))
    }

    @Test
    fun `dentro del carril interactivo la escritura va antes que el adhoc en cola`() {
        val order = mutableListOf<String>()
        val hold = CountDownLatch(1)
        val blockerStarted = CountDownLatch(1)

        bus.submit(CliJobType.ADHOC) {
            blockerStarted.countDown()
            hold.await(2, TimeUnit.SECONDS)
            order += "blocker"
            "blocker"
        }
        assertTrue(blockerStarted.await(2, TimeUnit.SECONDS))

        val adhoc = bus.submit(CliJobType.ADHOC) {
            order += "adhoc"
            "adhoc"
        }
        val write = bus.submit(CliJobType.WRITE) {
            order += "write"
            "write"
        }

        hold.countDown()
        assertEquals(CliBusResult.Ok("write"), write.get(2, TimeUnit.SECONDS))
        assertEquals(CliBusResult.Ok("adhoc"), adhoc.get(2, TimeUnit.SECONDS))
        assertEquals(listOf("blocker", "write", "adhoc"), order)
    }

    @Test
    fun `el keepalive late en las dos sesiones`() {
        bus.runKeepaliveTickForTest()

        Thread.sleep(150)
        verify(exactly = 1) { interactiveSession.ping() }
        verify(exactly = 1) { backgroundSession.ping() }
    }

    @Test
    fun `ping usa el carril interactivo`() {
        assertEquals(7L, bus.ping())

        verify(exactly = 1) { interactiveSession.ping() }
        verify(exactly = 0) { backgroundSession.ping() }
    }

    @Test
    fun `execute propaga OltUnreachableException sin envolver en ExecutionException`() {
        every { backgroundSession.execute(any<String>()) } throws
            OltUnreachableException("Unable to reach OLT at 10.11.104.2:22")

        val ex = assertThrows(OltUnreachableException::class.java) {
            bus.execute(CliJobType.INVENTORY) { it.execute("display board 0") }
        }

        assertEquals("Unable to reach OLT at 10.11.104.2:22", ex.message)
    }

    @Test
    fun `los trabajos de fondo se descartan rapido cuando la OLT esta degradada`() {
        val tracker = OltReachabilityTracker(failureThreshold = 1, backoffMs = 120_000) { 0L }
        tracker.recordFailure()
        val degradedBus = OltCliBus(
            sshClient = mockk(relaxed = true),
            properties = properties,
            sessionFactory = { _, _ -> newSession() },
            reachability = tracker
        )
        degradedBus.start()
        try {
            assertEquals(
                CliBusResult.Skipped("olt_unreachable"),
                degradedBus.execute(CliJobType.INVENTORY) { "should-not-run" }
            )
        } finally {
            degradedBus.close()
        }
    }

    @Test
    fun `una escritura sigue pasando con la OLT degradada`() {
        val tracker = OltReachabilityTracker(failureThreshold = 1, backoffMs = 120_000) { 0L }
        tracker.recordFailure()
        val degradedBus = OltCliBus(
            sshClient = mockk(relaxed = true),
            properties = properties,
            sessionFactory = { _, _ -> newSession() },
            reachability = tracker
        )
        degradedBus.start()
        try {
            assertEquals(
                CliBusResult.Ok("ok:undo shutdown"),
                degradedBus.execute(CliJobType.WRITE) { it.execute("undo shutdown") }
            )
        } finally {
            degradedBus.close()
        }
    }

    @Test
    fun `el estado agregado expone profundidad de cola y trabajo en curso`() {
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
        Thread.sleep(150)
        assertNull(bus.busyJobType())
        assertEquals(0, bus.queueDepth())
    }
}
