package com.dscorp.wispadmin.events

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class RedisOnuHashFieldsTest {

    private val now = Instant.parse("2026-09-09T20:00:00Z")
    private val later = Instant.parse("2026-09-09T20:05:00Z")

    @Test
    fun `optical with runState writes runState and observedAt`() {
        val hash = RedisOnuHashFields.forPut(
            LiveOnuState("SN1", "online", -19.5, later, updateKind = "optical"),
        )
        assertEquals("online", hash["runState"])
        assertEquals(later.toString(), hash["observedAt"])
        assertEquals(later.toString(), hash["opticalObservedAt"])
        assertEquals("-19.5", hash["rxPowerDbm"])
    }

    @Test
    fun `optical without runState does not overwrite state fields`() {
        val hash = RedisOnuHashFields.forPut(
            LiveOnuState("SN1", null, -20.0, later, updateKind = "optical"),
        )
        assertFalse(hash.containsKey("runState"))
        assertFalse(hash.containsKey("observedAt"))
        assertEquals(later.toString(), hash["opticalObservedAt"])
        assertEquals("-20.0", hash["rxPowerDbm"])
    }

    @Test
    fun `state update writes runState and observedAt without rx`() {
        val hash = RedisOnuHashFields.forPut(
            LiveOnuState("SN1", "offline", null, now, updateKind = "state"),
        )
        assertEquals("offline", hash["runState"])
        assertEquals(now.toString(), hash["observedAt"])
        assertFalse(hash.containsKey("rxPowerDbm"))
        assertTrue(hash.containsKey("sn"))
    }
}
