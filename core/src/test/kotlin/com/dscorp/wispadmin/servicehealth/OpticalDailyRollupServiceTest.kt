package com.dscorp.wispadmin.servicehealth

import com.dscorp.wispadmin.servicehealth.config.ServiceHealthProperties
import com.dscorp.wispadmin.servicehealth.domain.HealthCursor
import com.dscorp.wispadmin.servicehealth.domain.OpticalDailySample
import com.dscorp.wispadmin.servicehealth.domain.OpticalSample
import com.dscorp.wispadmin.servicehealth.domain.WifiAggregationWatermark
import com.dscorp.wispadmin.servicehealth.repository.HealthCursorRepository
import com.dscorp.wispadmin.servicehealth.repository.OpticalDailySampleRepository
import com.dscorp.wispadmin.servicehealth.repository.OpticalSampleRepository
import com.dscorp.wispadmin.servicehealth.repository.WifiAggregationWatermarkRepository
import com.dscorp.wispadmin.servicehealth.service.OpticalDailyRollupService
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.Optional

class OpticalDailyRollupServiceTest {
    private val samples = mockk<OpticalSampleRepository>()
    private val dailies = mockk<OpticalDailySampleRepository>(relaxed = true)
    private val watermarks = mockk<WifiAggregationWatermarkRepository>()
    private val cursors = mockk<HealthCursorRepository>()
    private val service = OpticalDailyRollupService(
        ServiceHealthProperties().apply { enabled = true; opticalEnabled = true },
        samples,
        dailies,
        watermarks,
        cursors,
    )

    @Test
    fun `rollup closed utc day computes min avg max count`() {
        val day = Instant.parse("2026-09-08T00:00:00Z")
        val now = Instant.parse("2026-09-09T00:10:00Z")
        every { cursors.lock("optical-daily-rollup") } returns HealthCursor(cursorKey = "optical-daily-rollup")
        every { watermarks.findById("OPTICAL_DAILY") } returns Optional.empty()
        every { samples.findTopByOrderByObservedAtAsc() } returns sample(day.plusSeconds(3600), -20.0, 2.0, -15.0)
        every { samples.findByObservedAtRange(day, day.plusSeconds(86400)) } returns listOf(
            sample(day.plusSeconds(3600), -20.0, 2.0, -15.0),
            sample(day.plusSeconds(7200), -24.0, 2.5, -16.0),
            sample(day.plusSeconds(10800), -22.0, null, -14.0),
        )
        val saved = slot<OpticalDailySample>()
        every { dailies.save(capture(saved)) } answers { firstArg() }
        val mark = slot<WifiAggregationWatermark>()
        every { watermarks.save(capture(mark)) } answers { firstArg() }

        service.catchUp(now)

        assertEquals(day, saved.captured.bucketStart)
        assertEquals(-24.0, saved.captured.onuRxMin)
        assertEquals(-22.0, saved.captured.onuRxAvg)
        assertEquals(-20.0, saved.captured.onuRxMax)
        assertEquals(2.0, saved.captured.onuTxMin)
        assertEquals(2.25, saved.captured.onuTxAvg)
        assertEquals(2.5, saved.captured.onuTxMax)
        assertEquals(-16.0, saved.captured.oltRxMin)
        assertEquals(-15.0, saved.captured.oltRxAvg)
        assertEquals(-14.0, saved.captured.oltRxMax)
        assertEquals(3, saved.captured.sampleCount)
        assertEquals(Instant.parse("2026-09-09T00:00:00Z"), mark.captured.consolidatedThrough)
        assertEquals("OPTICAL_DAILY", mark.captured.layer)
    }

    @Test
    fun `open utc day is not rolled up`() {
        val day = Instant.parse("2026-09-09T00:00:00Z")
        every { cursors.lock("optical-daily-rollup") } returns HealthCursor(cursorKey = "optical-daily-rollup")
        every { watermarks.findById("OPTICAL_DAILY") } returns Optional.empty()
        every { samples.findTopByOrderByObservedAtAsc() } returns sample(day.plusSeconds(300), -20.0, 2.0, -15.0)

        service.catchUp(day.plusSeconds(600))

        verify(exactly = 0) { dailies.save(any()) }
        verify(exactly = 0) { watermarks.save(any()) }
    }

    private fun sample(at: Instant, rx: Double?, tx: Double?, olt: Double?) = OpticalSample(
        subscriptionId = 2389,
        onuId = 7,
        onuSn = "VSOL1",
        oltId = 1,
        board = 1,
        port = 6,
        onuIndex = 1,
        observedAt = at,
        onuRxDbm = rx,
        onuTxDbm = tx,
        oltRxDbm = olt,
    )
}
