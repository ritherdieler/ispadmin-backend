package com.dscorp.wispadmin.servicehealth

import com.dscorp.wispadmin.servicehealth.config.ServiceHealthProperties
import com.dscorp.wispadmin.servicehealth.domain.HealthCursor
import com.dscorp.wispadmin.servicehealth.domain.WifiAggregationWatermark
import com.dscorp.wispadmin.servicehealth.domain.WifiStationHourly
import com.dscorp.wispadmin.servicehealth.domain.WifiStationSample
import com.dscorp.wispadmin.servicehealth.repository.HealthCursorRepository
import com.dscorp.wispadmin.servicehealth.repository.WifiAggregationWatermarkRepository
import com.dscorp.wispadmin.servicehealth.repository.WifiStationHourlyRepository
import com.dscorp.wispadmin.servicehealth.repository.WifiStationSampleRepository
import com.dscorp.wispadmin.servicehealth.service.WifiStationRollupService
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.Optional

class WifiStationRollupServiceTest {
    private val stations = mockk<WifiStationSampleRepository>()
    private val hourlies = mockk<WifiStationHourlyRepository>(relaxed = true)
    private val watermarks = mockk<WifiAggregationWatermarkRepository>()
    private val cursors = mockk<HealthCursorRepository>()
    private val service = WifiStationRollupService(
        ServiceHealthProperties(),
        stations,
        hourlies,
        watermarks,
        cursors,
    )

    @Test
    fun `rollup closed hour computes min avg max count and last display name`() {
        val hour = Instant.parse("2026-08-31T17:00:00Z")
        val now = Instant.parse("2026-08-31T18:10:00Z")
        every { cursors.lock("wifi-hourly-rollup") } returns HealthCursor(cursorKey = "wifi-hourly-rollup")
        every { watermarks.findById("HOURLY") } returns Optional.empty()
        every { stations.findTopByOrderByObservedAtAsc() } returns sample(hour.plusSeconds(600), -40.0, 30.0, "Mac")
        every { stations.findByObservedAtRange(hour, hour.plusSeconds(3600)) } returns listOf(
            sample(hour.plusSeconds(600), -40.0, 30.0, "Mac"),
            sample(hour.plusSeconds(1800), -50.0, 20.0, "MacBook"),
            sample(hour.plusSeconds(2400), -60.0, null, null),
        )
        val saved = slot<WifiStationHourly>()
        every { hourlies.save(capture(saved)) } answers { firstArg() }
        val mark = slot<WifiAggregationWatermark>()
        every { watermarks.save(capture(mark)) } answers { firstArg() }

        service.catchUp(now)

        assertEquals(-60.0, saved.captured.rssiMin)
        assertEquals(-50.0, saved.captured.rssiAvg)
        assertEquals(-40.0, saved.captured.rssiMax)
        assertEquals(20.0, saved.captured.snrMin)
        assertEquals(25.0, saved.captured.snrAvg)
        assertEquals(3, saved.captured.sampleCount)
        assertEquals("MacBook", saved.captured.displayName)
        assertEquals(hour, saved.captured.bucketStart)
        assertEquals(Instant.parse("2026-08-31T18:00:00Z"), mark.captured.consolidatedThrough)
    }

    @Test
    fun `open hour is not rolled up`() {
        val hour = Instant.parse("2026-08-31T18:00:00Z")
        every { cursors.lock("wifi-hourly-rollup") } returns HealthCursor(cursorKey = "wifi-hourly-rollup")
        every { watermarks.findById("HOURLY") } returns Optional.empty()
        every { stations.findTopByOrderByObservedAtAsc() } returns sample(hour.plusSeconds(300), -40.0, 20.0, "Mac")

        service.catchUp(hour.plusSeconds(600))

        verify(exactly = 0) { hourlies.save(any()) }
        verify(exactly = 0) { watermarks.save(any()) }
    }

    private fun sample(at: Instant, rssi: Double?, snr: Double?, name: String?) = WifiStationSample(
        subscriptionId = 2329,
        stationKey = "sta1",
        band = "5",
        displayName = name,
        observedAt = at,
        rssi = rssi,
        snr = snr,
    )
}
