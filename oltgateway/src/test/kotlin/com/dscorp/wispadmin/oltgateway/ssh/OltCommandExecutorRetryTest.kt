package com.dscorp.wispadmin.oltgateway.ssh

import com.dscorp.wispadmin.oltgateway.exception.OltUnreachableException
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class OltCommandExecutorRetryTest {

    private val cliBus = mockk<OltCliBus>()
    private val executor = OltCommandExecutor(cliBus, maxRetryAttempts = 3, retryDelayMs = 1)

    @Test
    fun `run without connection failure succeeds immediately`() {
        every { cliBus.execute(CliJobType.ADHOC, any<(HuaweiCliSession) -> String>()) } returns CliBusResult.Ok("OK")

        val result = executor.run("show version")

        assertEquals("OK", result)
        verify(exactly = 1) { cliBus.execute(CliJobType.ADHOC, any<(HuaweiCliSession) -> String>()) }
    }

    @Test
    fun `run with connection failure retries and succeeds`() {
        every { cliBus.execute(CliJobType.ADHOC, any<(HuaweiCliSession) -> String>()) }
            .throws(OltUnreachableException("Connection refused"))
            .andThen(CliBusResult.Ok("OK"))

        val result = executor.run("ont add 1 1 sn-auth VSOL123 omci")

        assertEquals("OK", result)
        verify(exactly = 2) { cliBus.execute(CliJobType.ADHOC, any<(HuaweiCliSession) -> String>()) }
    }

    @Test
    fun `run with IO exception containing connection refused retries`() {
        every { cliBus.execute(CliJobType.ADHOC, any<(HuaweiCliSession) -> String>()) }
            .throws(java.io.IOException("Connection refused"))
            .andThen(CliBusResult.Ok("Success"))

        val result = executor.run("interface gpon 0/1")

        assertEquals("Success", result)
        verify(exactly = 2) { cliBus.execute(CliJobType.ADHOC, any<(HuaweiCliSession) -> String>()) }
    }

    @Test
    fun `run with timeout exception retries`() {
        every { cliBus.execute(CliJobType.ADHOC, any<(HuaweiCliSession) -> String>()) }
            .throws(java.net.SocketTimeoutException("Read timeout"))
            .andThen(CliBusResult.Ok("MA5608T#"))

        val result = executor.run("quit")

        assertEquals("MA5608T#", result)
        verify(exactly = 2) { cliBus.execute(CliJobType.ADHOC, any<(HuaweiCliSession) -> String>()) }
    }

    @Test
    fun `run fails after max retry attempts`() {
        every { cliBus.execute(CliJobType.ADHOC, any<(HuaweiCliSession) -> String>()) }
            .throws(OltUnreachableException("SSH session down"))

        val exception = assertThrows<OltUnreachableException> {
            executor.run("ont delete 1 1")
        }

        assertTrue(exception.message!!.contains("SSH session down"))
        verify(exactly = 4) { cliBus.execute(CliJobType.ADHOC, any<(HuaweiCliSession) -> String>()) }
    }

    @Test
    fun `run retries indefinitely on connection failures until success`() {
        val unlimited = OltCommandExecutor(cliBus, maxRetryAttempts = 0, retryDelayMs = 1)
        every { cliBus.execute(CliJobType.ADHOC, any<(HuaweiCliSession) -> String>()) }
            .throws(OltUnreachableException("SSH session down"))
            .andThenThrows(java.io.IOException("Connection refused"))
            .andThenThrows(java.net.SocketTimeoutException("Read timeout"))
            .andThen(CliBusResult.Ok("MA5608T#"))

        val result = unlimited.run("ont delete 1 1")

        assertEquals("MA5608T#", result)
        verify(exactly = 4) { cliBus.execute(CliJobType.ADHOC, any<(HuaweiCliSession) -> String>()) }
    }

    @Test
    fun `run with unlimited retries eventually succeeds after many connection failures`() {
        val executorUnlimited = OltCommandExecutor(cliBus, maxRetryAttempts = 0, retryDelayMs = 1)

        every { cliBus.execute(CliJobType.ADHOC, any<(HuaweiCliSession) -> String>()) }
            .throws(OltUnreachableException("Connection down"))
            .andThenThrows(OltUnreachableException("Connection down"))
            .andThenThrows(OltUnreachableException("Connection down"))
            .andThenThrows(OltUnreachableException("Connection down"))
            .andThenThrows(OltUnreachableException("Connection down"))
            .andThenThrows(OltUnreachableException("Connection down"))
            .andThen(CliBusResult.Ok("Success"))

        val result = executorUnlimited.run("interface gpon 0/1")

        assertEquals("Success", result)
        verify(exactly = 7) { cliBus.execute(CliJobType.ADHOC, any<(HuaweiCliSession) -> String>()) }
    }

    @Test
    fun `run does not retry business logic errors`() {
        every { cliBus.execute(CliJobType.ADHOC, any<(HuaweiCliSession) -> String>()) }
            .throws(RuntimeException("The ont already exist"))

        val exception = assertThrows<RuntimeException> {
            executor.run("ont add 1 1 sn-auth EXISTING123 omci")
        }

        assertTrue(exception.message!!.contains("already exist"))
        verify(exactly = 1) { cliBus.execute(CliJobType.ADHOC, any<(HuaweiCliSession) -> String>()) }
    }

    @Test
    fun `write method also retries connection failures`() {
        every { cliBus.execute(CliJobType.WRITE, any<(HuaweiCliSession) -> String>()) }
            .throws(java.io.IOException("Connection reset"))
            .andThen(CliBusResult.Ok("Done"))

        val result = executor.write { it.execute("service-port vlan 100") }

        assertEquals("Done", result)
        verify(exactly = 2) { cliBus.execute(CliJobType.WRITE, any<(HuaweiCliSession) -> String>()) }
    }

    @Test fun `bounded write does not inherit unlimited SSH retries`() {
        val unlimited = OltCommandExecutor(cliBus, maxRetryAttempts = 0, retryDelayMs = 1)
        every { cliBus.execute(CliJobType.WRITE, any<(HuaweiCliSession) -> String>()) }
            .throws(OltUnreachableException("SSH session down"))

        assertThrows<OltUnreachableException> { unlimited.writeBounded(maxRetryAttempts = 2) { it.execute("ont ipconfig") } }

        verify(exactly = 3) { cliBus.execute(CliJobType.WRITE, any<(HuaweiCliSession) -> String>()) }
    }

    @Test
    fun `authorize method retries connection failures`() {
        every { cliBus.execute(CliJobType.AUTHORIZE, any<(HuaweiCliSession) -> String>()) }
            .throws(OltUnreachableException("Unable to reach OLT"))
            .andThen(CliBusResult.Ok("authorized"))

        val result = executor.authorize { it.execute("ont add 1 1") }

        assertEquals("authorized", result)
        verify(exactly = 2) { cliBus.execute(CliJobType.AUTHORIZE, any<(HuaweiCliSession) -> String>()) }
    }
}
