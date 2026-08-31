package com.dscorp.wispadmin.servicehealth

import com.dscorp.wispadmin.servicehealth.domain.UtcInstantText
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant

class UtcInstantTextTest {
    @Test
    fun `formats Instant as UTC wall clock without Lima shift`() {
        val instant = Instant.parse("2026-08-31T17:15:22.123456789Z")
        assertEquals("2026-08-31 17:15:22.123456", UtcInstantText.format(instant))
    }
}
