package com.dscorp.wispadmin.oltgateway.ssh

import com.dscorp.wispadmin.oltgateway.config.OltGatewayProperties
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.apache.sshd.client.SshClient
import org.apache.sshd.client.future.ConnectFuture
import org.apache.sshd.client.session.ClientSession
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class OltSshClientTest {

    private val properties = OltGatewayProperties().apply {
        host = "10.11.104.2"
        port = 22
        username = "oltadmin"
        password = "secret"
        commandTimeoutMs = 5000
        session.sshIdleTimeoutMinutes = 0
        session.keepaliveIntervalMs = 60000
    }

    @Test
    fun `openSession dos veces reutiliza el mismo SshClient started`() {
        val client = mockStartedClient()
        val factoryCalls = AtomicInteger(0)
        val sshClient = OltSshClient(properties) {
            factoryCalls.incrementAndGet()
            client
        }

        val first = sshClient.openSession()
        val second = sshClient.openSession()

        assertSame(client, first.client)
        assertSame(client, second.client)
        assertEquals(1, factoryCalls.get())
        verify(exactly = 1) { client.start() }
        verify(exactly = 2) { client.connect("oltadmin", "10.11.104.2", 22) }

        sshClient.close()
    }

    @Test
    fun `close de ConnectedSession no detiene el cliente global`() {
        val client = mockStartedClient()
        val sshClient = OltSshClient(properties) { client }

        val connected = sshClient.openSession()
        sshClient.close(connected)

        verify(exactly = 1) { connected.session.close() }
        verify(exactly = 0) { client.stop() }

        sshClient.close()
        verify(exactly = 1) { client.stop() }
    }

    private fun mockStartedClient(): SshClient {
        val client = mockk<SshClient>(relaxed = true)
        every { client.isStarted } returns true

        val session = mockk<ClientSession>(relaxed = true)
        val connectFuture = mockk<ConnectFuture>(relaxed = true)
        every { connectFuture.session } returns session
        every { connectFuture.verify(any<Long>(), any<TimeUnit>()) } returns connectFuture
        every { client.connect(any<String>(), any<String>(), any<Int>()) } returns connectFuture

        val authFuture = mockk<org.apache.sshd.client.future.AuthFuture>(relaxed = true)
        every { authFuture.verify(any<Long>(), any<TimeUnit>()) } returns authFuture
        every { session.auth() } returns authFuture

        return client
    }
}
