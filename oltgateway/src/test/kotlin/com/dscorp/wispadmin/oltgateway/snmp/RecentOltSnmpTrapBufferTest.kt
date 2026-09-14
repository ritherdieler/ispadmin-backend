package com.dscorp.wispadmin.oltgateway.snmp

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant

class RecentOltSnmpTrapBufferTest {

    @Test
    fun `mantiene solo los N mas recientes en orden newest-first`() {
        val buffer = RecentOltSnmpTrapBuffer(capacity = 2)
        buffer.accept(sample("a", Instant.parse("2026-08-27T00:00:00Z")))
        buffer.accept(sample("b", Instant.parse("2026-08-27T00:00:01Z")))
        buffer.accept(sample("c", Instant.parse("2026-08-27T00:00:02Z")))

        val recent = buffer.recent()
        assertEquals(2, recent.size)
        assertEquals("c", recent[0].trapOid)
        assertEquals("b", recent[1].trapOid)
    }

    private fun sample(oid: String, at: Instant) = OltSnmpTrapEvent(
        receivedAt = at,
        sourceHost = "10.11.104.2",
        community = null,
        trapOid = oid,
        trapLabel = null,
        varbinds = emptyList()
    )
}
