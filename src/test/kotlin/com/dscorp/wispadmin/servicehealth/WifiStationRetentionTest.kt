package com.dscorp.wispadmin.servicehealth

import com.dscorp.wispadmin.servicehealth.service.WifiStationRetention
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.Instant

class WifiStationRetentionTest {
    private val now = Instant.parse("2026-08-31T18:00:00Z")

    @Test
    fun `raw purge stays gated on hourly watermark`() {
        assertNull(WifiStationRetention.rawCutoff(now, 14, null))
        assertEquals(
            Instant.parse("2026-08-10T18:00:00Z"),
            WifiStationRetention.rawCutoff(now, 14, Instant.parse("2026-08-10T18:00:00Z")),
        )
        assertEquals(
            Instant.parse("2026-08-17T18:00:00Z"),
            WifiStationRetention.rawCutoff(now, 14, Instant.parse("2026-08-30T00:00:00Z")),
        )
    }
}
