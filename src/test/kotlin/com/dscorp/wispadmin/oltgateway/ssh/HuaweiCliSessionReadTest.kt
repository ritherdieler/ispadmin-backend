package com.dscorp.wispadmin.oltgateway.ssh

import com.dscorp.wispadmin.oltgateway.config.OltGatewayProperties
import com.dscorp.wispadmin.oltgateway.exception.OltUnreachableException
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import org.apache.sshd.client.SshClient
import org.apache.sshd.client.channel.ChannelShell
import org.apache.sshd.client.channel.ClientChannelEvent
import org.apache.sshd.client.future.OpenFuture
import org.apache.sshd.client.session.ClientSession
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class HuaweiCliSessionReadTest {

    @Test
    fun `readUntil falla rapido si la sesion SSH muere durante el comando`() {
        val properties = OltGatewayProperties().apply {
            commandTimeoutMs = 30_000
            password = "secret"
            session.keepaliveEnabled = false
        }
        val openSessionCalls = AtomicInteger(0)
        var channelOpen = true
        var sessionOpen = true
        val outRef = AtomicReference<ByteArrayOutputStream>()
        val waitCount = AtomicInteger(0)

        val client = mockk<SshClient>(relaxed = true)
        val session = mockk<ClientSession>(relaxed = true)
        val channel = mockk<ChannelShell>(relaxed = true)
        val openFuture = mockk<OpenFuture>(relaxed = true)

        every { session.isOpen } answers { sessionOpen }
        every { channel.isOpen } answers { channelOpen }
        every { session.createShellChannel() } answers {
            channelOpen = true
            sessionOpen = true
            channel
        }
        every { channel.open() } returns openFuture
        every { openFuture.verify(any<Long>(), any<TimeUnit>()) } returns openFuture
        every { channel.setOut(any()) } answers { outRef.set(firstArg()) }
        every { channel.setErr(any()) } answers { outRef.set(firstArg()) }
        every { channel.invertedIn } returns object : OutputStream() {
            override fun write(b: Int) {}
        }
        every { channel.waitFor(any(), any<Long>()) } answers {
            if (waitCount.incrementAndGet() >= 2) {
                channelOpen = false
                sessionOpen = false
            }
            emptySet<ClientChannelEvent>()
        }

        val sshClient = mockk<OltSshClient>()
        every { sshClient.openSession() } answers {
            openSessionCalls.incrementAndGet()
            OltSshClient.ConnectedSession(client, session)
        }
        every { sshClient.close(any()) } just runs

        val cliSession = HuaweiCliSession(sshClient, properties) {
            throw IllegalStateException("keepalive scheduler not expected")
        }

        val started = System.currentTimeMillis()
        assertThrows<OltUnreachableException> {
            cliSession.execute("display ont optical-info 3 all")
        }
        val elapsed = System.currentTimeMillis() - started

        assertTrue(elapsed < 5_000, "expected fast fail, elapsedMs=$elapsed")
        assertTrue(openSessionCalls.get() >= 1, "expected at least one SSH session open")
    }
}
