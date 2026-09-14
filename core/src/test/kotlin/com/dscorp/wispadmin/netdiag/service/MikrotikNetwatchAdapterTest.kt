package com.dscorp.wispadmin.netdiag.service

import com.dscorp.wispadmin.routeros.port.MikrotikSession
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MikrotikNetwatchAdapterTest {

    private val adapter = MikrotikNetwatchAdapter()

    @Test
    fun `collect mapea netwatch print a snapshots`() {
        val session = mockk<MikrotikSession>()
        every { session.print("/tool/netwatch") } returns listOf(
            mapOf(
                "name" to "upstream-http",
                "host" to "1.1.1.1",
                "status" to "up",
                "type" to "http-get",
                "since" to "2026-07-26 10:00:00",
                "comment" to "upstream"
            ),
            mapOf(
                "name" to "upstream-dns",
                "host" to "8.8.8.8",
                "status" to "down",
                "type" to "dns",
                "comment" to "upstream"
            )
        )

        val rows = adapter.collect(session, TargetMonitorConfig())

        assertEquals(2, rows.size)
        assertEquals("upstream-http", rows[0].name)
        assertEquals("up", rows[0].status)
        assertEquals("upstream-dns", rows[1].name)
        assertEquals("down", rows[1].status)
        assertEquals("dns", rows[1].type)
    }

    @Test
    fun `collect filtra por netwatchNames cuando estan configurados`() {
        val session = mockk<MikrotikSession>()
        every { session.print("/tool/netwatch") } returns listOf(
            mapOf("name" to "upstream-http", "host" to "1.1.1.1", "status" to "down"),
            mapOf("name" to "lab-ping", "host" to "10.0.0.1", "status" to "down")
        )

        val rows = adapter.collect(
            session,
            TargetMonitorConfig(netwatchNames = listOf("upstream-http"))
        )

        assertEquals(1, rows.size)
        assertEquals("upstream-http", rows.single().name)
    }

    @Test
    fun `collect retorna vacio si print falla`() {
        val session = mockk<MikrotikSession>()
        every { session.print("/tool/netwatch") } throws RuntimeException("unsupported")

        val rows = adapter.collect(session, TargetMonitorConfig())

        assertTrue(rows.isEmpty())
    }
}
