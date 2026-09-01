package com.dscorp.wispadmin.servicehealth.service

import java.time.Duration
import java.time.Instant

object WifiStationRetention {
    fun rawCutoff(now: Instant, retentionDays: Long, watermark: Instant?): Instant? {
        if (watermark == null) return null
        return minOf(now.minus(Duration.ofDays(retentionDays.coerceAtLeast(1))), watermark)
    }
}
