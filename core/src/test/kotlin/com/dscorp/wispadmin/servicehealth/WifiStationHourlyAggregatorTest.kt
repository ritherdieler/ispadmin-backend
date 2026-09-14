package com.dscorp.wispadmin.servicehealth

import com.dscorp.wispadmin.servicehealth.service.WifiStationHourlyAggregator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.Instant

class WifiStationHourlyAggregatorTest {
    private val hour = Instant.parse("2026-08-31T17:00:00Z")

    @Test
    fun `aggregates min avg max count and last display name for a closed hour`() {
        val buckets = WifiStationHourlyAggregator.aggregate(
            listOf(
                sample(rssi = -40.0, snr = 30.0, name = "Mac", at = hour.plusSeconds(600)),
                sample(rssi = -50.0, snr = 20.0, name = "MacBook", at = hour.plusSeconds(1800)),
                sample(rssi = -60.0, snr = null, name = null, at = hour.plusSeconds(2400)),
                sample(rssi = -10.0, snr = 99.0, name = "Outside", at = hour.plusSeconds(3600)),
            ),
            hour,
            hour.plusSeconds(3600),
        )
        val bucket = buckets.single()
        assertEquals(hour, bucket.bucketStart)
        assertEquals(-60.0, bucket.rssiMin)
        assertEquals(-50.0, bucket.rssiAvg)
        assertEquals(-40.0, bucket.rssiMax)
        assertEquals(20.0, bucket.snrMin)
        assertEquals(25.0, bucket.snrAvg)
        assertEquals(3, bucket.sampleCount)
        assertEquals("MacBook", bucket.displayName)
    }

    @Test
    fun `truncates to utc hour and keeps stations separate`() {
        assertEquals(hour, WifiStationHourlyAggregator.bucketStart(hour.plusSeconds(3599)))
        val buckets = WifiStationHourlyAggregator.aggregate(
            listOf(
                sample(key = "aaa", rssi = -30.0, at = hour.plusSeconds(10)),
                sample(key = "bbb", rssi = -80.0, at = hour.plusSeconds(20)),
            ),
            hour,
            hour.plusSeconds(3600),
        )
        assertEquals(listOf("aaa", "bbb"), buckets.map { it.stationKey }.sorted())
        assertEquals(-30.0, buckets.single { it.stationKey == "aaa" }.rssiMin)
        assertEquals(-80.0, buckets.single { it.stationKey == "bbb" }.rssiMin)
        assertNull(buckets.single { it.stationKey == "aaa" }.snrMin)
    }

    private fun sample(
        key: String = "sta1",
        rssi: Double?,
        snr: Double? = null,
        name: String? = "Mac",
        at: Instant,
    ) = WifiStationHourlyAggregator.Sample(
        subscriptionId = 2329,
        stationKey = key,
        band = "5",
        observedAt = at,
        rssi = rssi,
        snr = snr,
        displayName = name,
    )
}
