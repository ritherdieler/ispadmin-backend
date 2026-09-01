package com.dscorp.wispadmin.servicehealth.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.temporal.ChronoUnit

class HealthSqlTimeTest {
    @Test
    fun `timestamp drops fractional seconds so MySQL datetime matches the lookup`() {
        val observed = Instant.parse("2026-09-01T00:13:35.733Z")
        val stored = HealthSqlTime.timestamp(observed)
        assertEquals(Instant.parse("2026-09-01T00:13:35Z"), stored.toInstant())
        assertEquals(0, stored.toInstant().nano)
        assertEquals(HealthSqlTime.timestamp(observed.truncatedTo(ChronoUnit.SECONDS)), stored)
    }

    @Test
    fun `count sample id prefers observed lookup and falls back to the row just upserted`() {
        assertEquals(1183L, AcsWifiSampleLookup.idAfterUpsert(1183L, 9L, 7L))
        assertEquals(9L, AcsWifiSampleLookup.idAfterUpsert(null, 9L, 7L))
        assertEquals(7L, AcsWifiSampleLookup.idAfterUpsert(null, null, 7L))
        assertThrows(IllegalStateException::class.java) {
            AcsWifiSampleLookup.idAfterUpsert(null, null, null)
        }.also { assertEquals("ACS_SAMPLE_ID_NOT_FOUND", it.message) }
    }
}
