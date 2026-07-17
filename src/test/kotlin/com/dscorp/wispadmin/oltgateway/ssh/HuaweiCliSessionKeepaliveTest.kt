package com.dscorp.wispadmin.oltgateway.ssh

import com.dscorp.wispadmin.oltgateway.config.OltGatewayProperties
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.apache.sshd.client.SshClient
import org.apache.sshd.client.channel.ChannelShell
import org.apache.sshd.client.channel.ClientChannelEvent
import org.apache.sshd.client.future.OpenFuture
import org.apache.sshd.client.session.ClientSession
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class HuaweiCliSessionKeepaliveTest {

    private val sshClient = mockk<OltSshClient>()
    private val keepaliveTasks = mutableListOf<Runnable>()
    private val scheduler = mockk<ScheduledExecutorService>()
    private val openSessionCalls = AtomicInteger(0)
    private var channelOpen = true
    private var sessionOpen = true
    private lateinit var cliSession: HuaweiCliSession

    @AfterEach
    fun tearDown() {
        if (::cliSession.isInitialized) {
            cliSession.close()
        }
    }

    @Test
    fun `keepalive no reabre sesion si sigue abierta`() {
        val properties = baseProperties()
        setupShellMocks()
        cliSession = HuaweiCliSession(sshClient, properties) { scheduler }
        cliSession.start()

        cliSession.execute("display ont info by-sn ZTEGDC47DF15")
        val opensAfterExecute = openSessionCalls.get()

        runKeepaliveTicks()

        assertTrue(opensAfterExecute >= 1)
        verify(exactly = opensAfterExecute) { sshClient.openSession() }
    }

    @Test
    fun `si canal cerrado keepalive reconecta y prepara`() {
        val properties = baseProperties()
        setupShellMocks()
        cliSession = HuaweiCliSession(sshClient, properties) { scheduler }
        cliSession.start()
        cliSession.execute("display version")
        val opensBefore = openSessionCalls.get()

        channelOpen = false
        runKeepaliveTicks()

        assertTrue(openSessionCalls.get() > opensBefore)
    }

    @Test
    fun `close detiene el keepalive`() {
        val properties = baseProperties()
        setupShellMocks()
        cliSession = HuaweiCliSession(sshClient, properties) { scheduler }
        cliSession.start()

        cliSession.close()

        verify(exactly = 1) { scheduler.shutdownNow() }
    }

    @Test
    fun `ping usa keepaliveCommand y healthTimeoutMs`() {
        val properties = baseProperties().apply {
            session.keepaliveCommand = "display clock"
            session.healthTimeoutMs = 5000
        }
        val written = setupShellMocks(captureWrites = true)
        cliSession = HuaweiCliSession(sshClient, properties) { scheduler }
        cliSession.start()

        val latency = cliSession.ping()

        assertTrue(latency >= 0)
        assertTrue(written.any { it.contains("display clock") })
        assertTrue(written.none { it.contains("display version") })
    }

    @Test
    fun `prepareSession habilita mmi-mode para salida sin paginacion`() {
        val written = setupShellMocks(captureWrites = true)
        cliSession = HuaweiCliSession(sshClient, baseProperties()) { scheduler }

        cliSession.execute("display clock")

        val joined = written.joinToString("")
        assertTrue(joined.contains("config"))
        assertTrue(joined.contains("mmi-mode enable"))
        assertTrue(joined.contains("quit"))
        assertTrue(!joined.contains("screen-length"))
        assertTrue(!joined.contains("scroll 512"))
    }

    private fun baseProperties() = OltGatewayProperties().apply {
        commandTimeoutMs = 2000
        password = "secret"
        session.keepaliveEnabled = true
        session.keepaliveIntervalMs = 50
        session.keepaliveCommand = "display clock"
        session.healthTimeoutMs = 1000
        session.sshIdleTimeoutMinutes = 0
    }

    private fun runKeepaliveTicks() {
        keepaliveTasks.toList().forEach { it.run() }
    }

    private fun setupShellMocks(captureWrites: Boolean = false): MutableList<String> {
        val written = mutableListOf<String>()
        val client = mockk<SshClient>(relaxed = true)
        val session = mockk<ClientSession>(relaxed = true)
        val channel = mockk<ChannelShell>(relaxed = true)
        val openFuture = mockk<OpenFuture>(relaxed = true)
        val outRef = AtomicReference<ByteArrayOutputStream>()
        openSessionCalls.set(0)

        every { session.isOpen } answers { sessionOpen }
        every { channel.isOpen } answers { channelOpen }
        every { session.createShellChannel() } answers {
            channelOpen = true
            sessionOpen = true
            channel
        }
        every { channel.open() } returns openFuture
        every { openFuture.verify(any<Long>(), any<TimeUnit>()) } returns openFuture

        every { channel.setOut(any()) } answers {
            outRef.set(firstArg())
        }
        every { channel.setErr(any()) } answers {
            outRef.set(firstArg())
        }

        val invertedIn = object : OutputStream() {
            override fun write(b: Int) {}

            override fun write(b: ByteArray, off: Int, len: Int) {
                if (captureWrites) {
                    written.add(String(b, off, len))
                }
            }
        }
        every { channel.invertedIn } returns invertedIn

        every { channel.waitFor(any(), any<Long>()) } answers {
            outRef.get()?.write("MA5608T#\r\n".toByteArray())
            emptySet<ClientChannelEvent>()
        }

        every { sshClient.openSession() } answers {
            openSessionCalls.incrementAndGet()
            OltSshClient.ConnectedSession(client, session)
        }
        every { sshClient.close(any()) } just runs

        every {
            scheduler.scheduleWithFixedDelay(any(), any(), any(), any())
        } answers {
            keepaliveTasks.add(firstArg())
            mockk<ScheduledFuture<*>>(relaxed = true)
        }
        every { scheduler.shutdownNow() } returns emptyList()

        return written
    }
}
