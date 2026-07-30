package com.dscorp.wispadmin.routeros

import com.dscorp.wispadmin.routeros.adapter.RouterOsRestClassicFallbackAdapter
import com.dscorp.wispadmin.routeros.port.MikrotikAuthException
import com.dscorp.wispadmin.routeros.port.MikrotikClient
import com.dscorp.wispadmin.routeros.port.MikrotikCommandException
import com.dscorp.wispadmin.routeros.port.MikrotikDeviceRef
import com.dscorp.wispadmin.routeros.port.MikrotikSession
import com.dscorp.wispadmin.routeros.port.MikrotikUnreachableException
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class RouterOsRestClassicFallbackAdapterTest {

    private val device = MikrotikDeviceRef(
        id = "mk1",
        host = "38.224.231.2",
        port = 443,
        username = "admin",
        password = "secret"
    )

    @Test
    fun `usa REST cuando responde`() {
        val primary = mockk<MikrotikClient>()
        val fallback = mockk<MikrotikClient>()
        val blockSlot = slot<(MikrotikSession) -> String>()
        every { primary.withSession(device, capture(blockSlot)) } answers {
            blockSlot.captured(mockk())
        }

        val adapter = RouterOsRestClassicFallbackAdapter(primary, fallback)
        val result = adapter.withSession(device) { "ok" }

        assertEquals("ok", result)
        verify(exactly = 0) { fallback.withSession(any(), any<(MikrotikSession) -> Any>()) }
    }

    @Test
    fun `cae a classic ante handshake failure en REST`() {
        val primary = mockk<MikrotikClient>()
        val fallback = mockk<MikrotikClient>()
        every { primary.withSession(device, any<(MikrotikSession) -> Any>()) } throws MikrotikCommandException(
            "rest POST /rest/system/identity/print: Received fatal alert: handshake_failure"
        )
        val blockSlot = slot<(MikrotikSession) -> String>()
        every { fallback.withSession(any(), capture(blockSlot)) } answers {
            blockSlot.captured(mockk())
        }

        val adapter = RouterOsRestClassicFallbackAdapter(primary, fallback)
        val result = adapter.withSession(device) { "classic" }

        assertEquals("classic", result)
        verify { fallback.withSession(any(), any<(MikrotikSession) -> Any>()) }
    }

    @Test
    fun `cae a classic ante unreachable REST`() {
        val primary = mockk<MikrotikClient>()
        val fallback = mockk<MikrotikClient>()
        every { primary.withSession(device, any<(MikrotikSession) -> Any>()) } throws MikrotikUnreachableException("connection refused")
        val blockSlot = slot<(MikrotikSession) -> String>()
        every { fallback.withSession(any(), capture(blockSlot)) } answers {
            blockSlot.captured(mockk())
        }

        val adapter = RouterOsRestClassicFallbackAdapter(primary, fallback)
        adapter.withSession(device) { "classic" }

        verify { fallback.withSession(any(), any<(MikrotikSession) -> Any>()) }
    }

    @Test
    fun `no hace fallback en auth invalida`() {
        val primary = mockk<MikrotikClient>()
        val fallback = mockk<MikrotikClient>()
        every { primary.withSession(device, any<(MikrotikSession) -> Any>()) } throws MikrotikAuthException("unauthorized")

        val adapter = RouterOsRestClassicFallbackAdapter(primary, fallback)

        assertThrows<MikrotikAuthException> {
            adapter.withSession(device) { "x" }
        }
        verify(exactly = 0) { fallback.withSession(any(), any<(MikrotikSession) -> Any>()) }
    }
}
