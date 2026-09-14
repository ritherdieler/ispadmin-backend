package com.dscorp.wispadmin.servicehealth

import com.dscorp.wispadmin.servicehealth.service.OpticalDailyAggregator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant

class OpticalDailyAggregatorTest {
    private val day = Instant.parse("2026-09-08T00:00:00Z")

    @Test
    fun `aggregates min avg max count for a closed utc day`() {
        val buckets = OpticalDailyAggregator.aggregate(
            listOf(
                sample(rx = -20.0, tx = 2.0, olt = -15.0, at = day.plusSeconds(3600)),
                sample(rx = -22.0, tx = 2.5, olt = -16.0, at = day.plusSeconds(7200)),
                sample(rx = -24.0, tx = null, olt = -14.0, at = day.plusSeconds(10800)),
                sample(rx = -10.0, tx = 9.0, olt = -1.0, at = day.plusSeconds(86400)),
            ),
            day,
            day.plusSeconds(86400),
        )
        val bucket = buckets.single()
        assertEquals(day, bucket.bucketStart)
        assertEquals(1L, bucket.onuId)
        assertEquals(-24.0, bucket.onuRxMin)
        assertEquals(-22.0, bucket.onuRxAvg)
        assertEquals(-20.0, bucket.onuRxMax)
        assertEquals(2.0, bucket.onuTxMin)
        assertEquals(2.25, bucket.onuTxAvg)
        assertEquals(2.5, bucket.onuTxMax)
        assertEquals(-16.0, bucket.oltRxMin)
        assertEquals(-15.0, bucket.oltRxAvg)
        assertEquals(-14.0, bucket.oltRxMax)
        assertEquals(3, bucket.sampleCount)
    }

    @Test
    fun `truncates to utc day and keeps onus separate`() {
        assertEquals(day, OpticalDailyAggregator.bucketStart(day.plusSeconds(86399)))
        val buckets = OpticalDailyAggregator.aggregate(
            listOf(
                sample(onuId = 1, rx = -20.0, at = day.plusSeconds(10)),
                sample(onuId = 2, rx = -30.0, at = day.plusSeconds(20)),
            ),
            day,
            day.plusSeconds(86400),
        )
        assertEquals(listOf(1L, 2L), buckets.map { it.onuId }.sorted())
        assertEquals(-20.0, buckets.single { it.onuId == 1L }.onuRxMin)
        assertEquals(-30.0, buckets.single { it.onuId == 2L }.onuRxMin)
    }

    private fun sample(
        onuId: Long = 1L,
        rx: Double?,
        tx: Double? = 2.0,
        olt: Double? = -15.0,
        at: Instant,
    ) = OpticalDailyAggregator.Sample(
        subscriptionId = 2389,
        onuId = onuId,
        onuSn = "SN$onuId",
        oltId = 1L,
        board = 1,
        port = 6,
        onuIndex = onuId.toInt(),
        observedAt = at,
        onuRxDbm = rx,
        onuTxDbm = tx,
        oltRxDbm = olt,
    )
}
