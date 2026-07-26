package com.dscorp.wispadmin.routeros

import com.dscorp.wispadmin.routeros.adapter.ClassicConnectionFactory
import com.dscorp.wispadmin.routeros.adapter.LegrangeClassicAdapter
import com.dscorp.wispadmin.routeros.config.RouterOsClientProperties
import com.dscorp.wispadmin.routeros.port.MikrotikAuthException
import com.dscorp.wispadmin.routeros.port.MikrotikCommandException
import com.dscorp.wispadmin.routeros.port.MikrotikDeviceRef
import com.dscorp.wispadmin.routeros.port.MikrotikTimeoutException
import com.dscorp.wispadmin.routeros.port.MikrotikUnreachableException
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import me.legrange.mikrotik.ApiConnection
import me.legrange.mikrotik.ApiConnectionException
import me.legrange.mikrotik.MikrotikApiException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.SocketTimeoutException

class LegrangeClassicAdapterUnitTest {

    private val device = MikrotikDeviceRef(
        id = "unit-classic",
        host = "10.0.0.1",
        port = 8728,
        username = "admin",
        password = "secret"
    )

    @Test
    fun `print system identity returns name`() {
        val connection = mockk<ApiConnection>(relaxed = true)
        every { connection.execute("/system/identity/print") } returns listOf(mapOf("name" to "MK1-UNIT"))
        val factory = ClassicConnectionFactory { _, _, _ -> connection }
        val adapter = LegrangeClassicAdapter(RouterOsClientProperties(), factory)

        val rows = adapter.withSession(device) { it.print("/system/identity") }

        assertEquals("MK1-UNIT", rows.first()["name"])
        verify(exactly = 1) { connection.login("admin", "secret") }
        adapter.close()
    }

    @Test
    fun `print system resource returns version`() {
        val connection = mockk<ApiConnection>(relaxed = true)
        every { connection.execute("/system/resource/print") } returns listOf(
            mapOf("version" to "7.23.2 (stable)")
        )
        val factory = ClassicConnectionFactory { _, _, _ -> connection }
        val adapter = LegrangeClassicAdapter(RouterOsClientProperties(), factory)

        val rows = adapter.withSession(device) { it.print("/system/resource") }

        assertEquals("7.23.2 (stable)", rows.first()["version"])
        adapter.close()
    }

    @Test
    fun `empty result from print is preserved`() {
        val connection = mockk<ApiConnection>(relaxed = true)
        every { connection.execute(any()) } returns emptyList()
        val factory = ClassicConnectionFactory { _, _, _ -> connection }
        val adapter = LegrangeClassicAdapter(RouterOsClientProperties(), factory)

        val rows = adapter.withSession(device) {
            it.print("/system/identity", mapOf("name" to "missing"))
        }

        assertTrue(rows.isEmpty())
        adapter.close()
    }

    @Test
    fun `login failure maps to MikrotikAuthException`() {
        val connection = mockk<ApiConnection>(relaxed = true)
        every { connection.login(any(), any()) } throws MikrotikApiException("cannot log in")
        val factory = ClassicConnectionFactory { _, _, _ -> connection }
        val adapter = LegrangeClassicAdapter(RouterOsClientProperties(), factory)

        assertThrows(MikrotikAuthException::class.java) {
            adapter.withSession(device) { it.print("/system/identity") }
        }
        adapter.close()
    }

    @Test
    fun `connection failure maps to MikrotikUnreachableException`() {
        val factory = ClassicConnectionFactory { _, _, _ ->
            throw ApiConnectionException("Connection refused")
        }
        val adapter = LegrangeClassicAdapter(RouterOsClientProperties(), factory)

        assertThrows(MikrotikUnreachableException::class.java) {
            adapter.withSession(device) { it.print("/system/identity") }
        }
        adapter.close()
    }

    @Test
    fun `timeout cause maps to MikrotikTimeoutException`() {
        val connection = mockk<ApiConnection>(relaxed = true)
        every { connection.execute(any()) } throws MikrotikApiException(
            "timeout",
            SocketTimeoutException("read timed out")
        )
        val factory = ClassicConnectionFactory { _, _, _ -> connection }
        val adapter = LegrangeClassicAdapter(RouterOsClientProperties(), factory)

        assertThrows(MikrotikTimeoutException::class.java) {
            adapter.withSession(device) { it.print("/system/identity") }
        }
        adapter.close()
    }

    @Test
    fun `command failure maps to MikrotikCommandException`() {
        val connection = mockk<ApiConnection>(relaxed = true)
        every { connection.execute(any()) } throws MikrotikApiException("no such command")
        val factory = ClassicConnectionFactory { _, _, _ -> connection }
        val adapter = LegrangeClassicAdapter(RouterOsClientProperties(), factory)

        assertThrows(MikrotikCommandException::class.java) {
            adapter.withSession(device) { it.print("/bogus/path") }
        }
        adapter.close()
    }

    @Test
    fun `withSession reuses pooled connection for same device id`() {
        val connection = mockk<ApiConnection>(relaxed = true)
        every { connection.isConnected } returns true
        every { connection.execute(any()) } returns listOf(mapOf("name" to "MK1"))
        var opens = 0
        val factory = ClassicConnectionFactory { _, _, _ ->
            opens++
            connection
        }
        val adapter = LegrangeClassicAdapter(RouterOsClientProperties(), factory)

        adapter.withSession(device) { it.print("/system/identity") }
        adapter.withSession(device) { it.print("/system/identity") }

        assertEquals(1, opens)
        adapter.close()
    }
}
