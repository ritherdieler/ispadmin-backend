package com.dscorp.wispadmin.observability.dto

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class FlexibleEpochMillisTest {

    @Test
    fun `acepta epoch millis como texto`() {
        assertEquals(1784043978140L, FlexibleEpochMillis.fromText("1784043978140"))
    }

    @Test
    fun `acepta timestamp ISO-8601`() {
        assertEquals(1784043978000L, FlexibleEpochMillis.fromText("2026-07-14T15:46:18.000Z"))
    }

    @Test
    fun `rechaza texto invalido`() {
        assertNull(FlexibleEpochMillis.fromText("not-a-timestamp"))
        assertNull(FlexibleEpochMillis.fromText(null))
        assertNull(FlexibleEpochMillis.fromText("  "))
    }
}
