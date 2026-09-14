package com.dscorp.wispadmin.oltgateway.ssh

import com.dscorp.wispadmin.oltgateway.config.OltGatewayProperties
import com.dscorp.wispadmin.oltgateway.exception.OltUnreachableException
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class OltCliBusTest {

    private val properties = OltGatewayProperties().apply {
        session.poolSize = 2
        session.keepaliveEnabled = true
        session.keepaliveIntervalMs = 60_000
        commandTimeoutMs = 5_000
    }

    private val sessions = CopyOnWriteArrayList<HuaweiCliSession>()
    private lateinit var bus: OltCliBus

    private val reservedSession: HuaweiCliSession get() = sessions[0]
    private val bulkSession: HuaweiCliSession get() = sessions[1]

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
    fun `abre dos sesiones una reservada y una de fondo`() {
        assertEquals(2, bus.sessionCount())
        assertEquals(2, sessions.size)
    }

    @Test
    fun `con pool size 1 degrada a una sola sesion y serializa inventario con authorize`() {
        val single = OltGatewayProperties().apply {
            session.poolSize = 1
            session.keepaliveEnabled = false
            commandTimeoutMs = 5_000
        }
        val singleBus = newBus(single)
        singleBus.start()
        try {
            assertEquals(1, singleBus.sessionCount())
            val hold = CountDownLatch(1)
            val started = CountDownLatch(1)
            val authorizeStarted = AtomicBoolean(false)
            val inventory = singleBus.submit(CliJobType.INVENTORY) {
                started.countDown()
                hold.await(2, TimeUnit.SECONDS)
                "inventory"
            }
            assertTrue(started.await(2, TimeUnit.SECONDS))
            val authorize = singleBus.submit(CliJobType.AUTHORIZE) {
                authorizeStarted.set(true)
                "auth"
            }
            Thread.sleep(80)
            assertFalse(authorizeStarted.get())
            hold.countDown()
            assertEquals(CliBusResult.Ok("inventory"), inventory.get(2, TimeUnit.SECONDS))
            assertEquals(CliBusResult.Ok("auth"), authorize.get(2, TimeUnit.SECONDS))
        } finally {
            singleBus.close()
        }
    }

    @Test
    fun `authorize corre en la reservada mientras el fondo hace inventario`() {
        val hold = CountDownLatch(1)
        val inventoryStarted = CountDownLatch(1)
        val inventory = bus.submit(CliJobType.INVENTORY) {
            inventoryStarted.countDown()
            hold.await(3, TimeUnit.SECONDS)
            "inventory"
        }
        assertTrue(inventoryStarted.await(2, TimeUnit.SECONDS))
        assertEquals(CliJobType.INVENTORY, bus.busyJobType(CliLane.BACKGROUND))
        assertNull(bus.busyJobType(CliLane.INTERACTIVE))

        val used = AtomicReference<HuaweiCliSession>()
        val authorize = bus.submit(CliJobType.AUTHORIZE) { session ->
            used.set(session)
            "auth"
        }

        assertEquals(CliBusResult.Ok("auth"), authorize.get(2, TimeUnit.SECONDS))
        assertSame(reservedSession, used.get())
        assertEquals(CliJobType.INVENTORY, bus.busyJobType(CliLane.BACKGROUND))

        hold.countDown()
        assertEquals(CliBusResult.Ok("inventory"), inventory.get(3, TimeUnit.SECONDS))
    }

    @Test
    fun `un inventario no ocupa la sesion reservada`() {
        val hold = CountDownLatch(1)
        val started = CountDownLatch(1)
        bus.submit(CliJobType.INVENTORY) {
            started.countDown()
            hold.await(2, TimeUnit.SECONDS)
            "inventory"
        }
        assertTrue(started.await(2, TimeUnit.SECONDS))
        assertNull(bus.busyJobType(CliLane.INTERACTIVE))
        assertEquals(CliJobType.INVENTORY, bus.busyJobType(CliLane.BACKGROUND))
        hold.countDown()
    }

    @Test
    fun `escritura corta usa el fondo cuando ambas estan libres`() {
        val used = AtomicReference<HuaweiCliSession>()
        bus.execute(CliJobType.WRITE) { session ->
            used.set(session)
            session.execute("ont delete")
        }
        assertSame(bulkSession, used.get())
        verify(exactly = 1) { bulkSession.execute("ont delete") }
        verify(exactly = 0) { reservedSession.execute("ont delete") }
    }

    @Test
    fun `escritura corta usa la reservada solo si el fondo esta ocupado`() {
        val hold = CountDownLatch(1)
        val inventoryStarted = CountDownLatch(1)
        bus.submit(CliJobType.INVENTORY) {
            inventoryStarted.countDown()
            hold.await(2, TimeUnit.SECONDS)
            "inventory"
        }
        assertTrue(inventoryStarted.await(2, TimeUnit.SECONDS))

        val used = AtomicReference<HuaweiCliSession>()
        bus.execute(CliJobType.WRITE) { session ->
            used.set(session)
            "delete"
        }
        assertSame(reservedSession, used.get())
        hold.countDown()
    }

    @Test
    fun `authorize espera un write en curso y luego salta la cola`() {
        val writeHold = CountDownLatch(1)
        val writeStarted = CountDownLatch(1)
        val order = CopyOnWriteArrayList<String>()
        val authorizeStarted = AtomicBoolean(false)

        val write = bus.submit(CliJobType.WRITE) {
            writeStarted.countDown()
            writeHold.await(2, TimeUnit.SECONDS)
            order += "write"
            "write"
        }
        assertTrue(writeStarted.await(2, TimeUnit.SECONDS))

        val adhoc = bus.submit(CliJobType.ADHOC) {
            order += "adhoc"
            "adhoc"
        }
        val authorize = bus.submit(CliJobType.AUTHORIZE) {
            authorizeStarted.set(true)
            order += "authorize"
            "auth"
        }

        Thread.sleep(80)
        assertFalse(authorizeStarted.get())
        assertTrue(bus.queueDepth() >= 1)

        writeHold.countDown()
        assertEquals(CliBusResult.Ok("write"), write.get(2, TimeUnit.SECONDS))
        assertEquals(CliBusResult.Ok("auth"), authorize.get(2, TimeUnit.SECONDS))
        assertEquals(CliBusResult.Ok("adhoc"), adhoc.get(2, TimeUnit.SECONDS))
        assertEquals("write", order.first())
        assertTrue(order.contains("authorize"))
        assertTrue(order.contains("adhoc"))
    }

    @Test
    fun `no cancela una lectura larga ni la peticion de no autorizadas`() {
        val inventoryHold = CountDownLatch(1)
        val unconfiguredHold = CountDownLatch(1)
        val inventoryStarted = CountDownLatch(1)
        val unconfiguredStarted = CountDownLatch(1)
        val authorizeStarted = AtomicBoolean(false)

        val inventory = bus.submit(CliJobType.INVENTORY) {
            inventoryStarted.countDown()
            inventoryHold.await(3, TimeUnit.SECONDS)
            "inventory"
        }
        assertTrue(inventoryStarted.await(2, TimeUnit.SECONDS))
        val unconfigured = bus.submit(CliJobType.UNCONFIGURED) {
            unconfiguredStarted.countDown()
            unconfiguredHold.await(3, TimeUnit.SECONDS)
            "unconfigured"
        }
        assertTrue(unconfiguredStarted.await(2, TimeUnit.SECONDS))

        val authorize = bus.submit(CliJobType.AUTHORIZE) {
            authorizeStarted.set(true)
            "auth"
        }
        Thread.sleep(80)
        assertFalse(authorizeStarted.get())
        assertEquals(CliJobType.INVENTORY, bus.busyJobType(CliLane.BACKGROUND))
        assertEquals(CliJobType.UNCONFIGURED, bus.busyJobType(CliLane.INTERACTIVE))

        unconfiguredHold.countDown()
        assertEquals(CliBusResult.Ok("unconfigured"), unconfigured.get(2, TimeUnit.SECONDS))
        assertEquals(CliBusResult.Ok("auth"), authorize.get(2, TimeUnit.SECONDS))
        assertFalse(inventory.isDone)
        inventoryHold.countDown()
        assertEquals(CliBusResult.Ok("inventory"), inventory.get(2, TimeUnit.SECONDS))
    }

    @Test
    fun `authorize toma el fondo si la reservada esta en no autorizadas y el fondo libre`() {
        val hold = CountDownLatch(1)
        val started = CountDownLatch(1)
        bus.submit(CliJobType.UNCONFIGURED) {
            started.countDown()
            hold.await(2, TimeUnit.SECONDS)
            "unconfigured"
        }
        assertTrue(started.await(2, TimeUnit.SECONDS))

        val used = AtomicReference<HuaweiCliSession>()
        val authorize = bus.submit(CliJobType.AUTHORIZE) { session ->
            used.set(session)
            "auth"
        }
        assertEquals(CliBusResult.Ok("auth"), authorize.get(2, TimeUnit.SECONDS))
        assertSame(bulkSession, used.get())
        hold.countDown()
    }

    @Test
    fun `dos lecturas de no autorizadas comparten un solo ssh`() {
        val hold = CountDownLatch(1)
        val started = CountDownLatch(1)
        val runs = AtomicInteger(0)

        val first = bus.submit(CliJobType.UNCONFIGURED) {
            runs.incrementAndGet()
            started.countDown()
            hold.await(2, TimeUnit.SECONDS)
            "shared"
        }
        assertTrue(started.await(2, TimeUnit.SECONDS))
        val second = bus.submit(CliJobType.UNCONFIGURED) {
            runs.incrementAndGet()
            "other"
        }

        hold.countDown()
        assertEquals(CliBusResult.Ok("shared"), first.get(2, TimeUnit.SECONDS))
        assertEquals(CliBusResult.Ok("shared"), second.get(2, TimeUnit.SECONDS))
        assertEquals(1, runs.get())
    }

    @Test
    fun `el fondo serializa inventario senal y alarmas entre si`() {
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
    fun `un submit anidado reutiliza la sesion en curso`() {
        val result = bus.execute(CliJobType.INVENTORY) { outer ->
            val nested = bus.execute(CliJobType.ADHOC) { inner -> inner.execute("nested") }
            (nested as CliBusResult.Ok).value + ":" + outer.execute("outer")
        }

        assertEquals(CliBusResult.Ok("ok:nested:ok:outer"), result)
        verify(exactly = 1) { bulkSession.execute("nested") }
        verify(exactly = 1) { bulkSession.execute("outer") }
        verify(exactly = 0) { reservedSession.execute(any()) }
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
    fun `el keepalive late en las dos sesiones`() {
        bus.runKeepaliveTickForTest()

        Thread.sleep(150)
        verify(exactly = 1) { reservedSession.ping() }
        verify(exactly = 1) { bulkSession.ping() }
    }

    @Test
    fun `ping usa la sesion de fondo`() {
        assertEquals(7L, bus.ping())

        verify(exactly = 1) { bulkSession.ping() }
        verify(exactly = 0) { reservedSession.ping() }
    }

    @Test
    fun `execute propaga OltUnreachableException sin envolver en ExecutionException`() {
        every { bulkSession.execute(any<String>()) } throws
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
    fun `un authorize sigue pasando con la OLT degradada`() {
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
                CliBusResult.Ok("ok:ont add"),
                degradedBus.execute(CliJobType.AUTHORIZE) { it.execute("ont add") }
            )
        } finally {
            degradedBus.close()
        }
    }

    @Test
    fun `el estado agregado expone profundidad de cola y trabajo en curso`() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)

        bus.submit(CliJobType.AUTHORIZE) {
            entered.countDown()
            release.await(2, TimeUnit.SECONDS)
            "w"
        }
        assertTrue(entered.await(2, TimeUnit.SECONDS))
        assertEquals(CliJobType.AUTHORIZE, bus.busyJobType())

        bus.submit(CliJobType.WRITE) { "queued-write" }
        assertTrue(bus.queueDepth() >= 1)

        release.countDown()
        Thread.sleep(150)
        assertNull(bus.busyJobType())
        assertEquals(0, bus.queueDepth())
    }
}
