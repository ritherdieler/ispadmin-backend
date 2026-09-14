package com.dscorp.wispadmin.servicehealth

import com.dscorp.wispadmin.servicehealth.domain.Quality
import com.dscorp.wispadmin.servicehealth.domain.UtcInstantText
import com.dscorp.wispadmin.servicehealth.domain.WifiStationHourly
import com.dscorp.wispadmin.servicehealth.domain.WifiStationSample
import com.dscorp.wispadmin.servicehealth.service.WifiSignalSeries
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class WifiSignalSeriesTest {
    private val to = Instant.parse("2026-08-31T18:00:00Z")

    @Test
    fun `windows of seven days or less stay raw`() {
        assertFalse(WifiSignalSeries.useHourly(to.minusSeconds(86400), to, 7))
        assertFalse(WifiSignalSeries.useHourly(to.minusSeconds(7 * 86400), to, 7))
        assertTrue(WifiSignalSeries.useHourly(to.minusSeconds(7 * 86400 + 1), to, 7))
        assertTrue(WifiSignalSeries.useHourly(to.minusSeconds(30 * 86400), to, 7))
    }

    @Test
    fun `hourly points expose rssi min as the plotted rssi`() {
        val at = Instant.parse("2026-08-31T17:00:00Z")
        val mapped = WifiSignalSeries.mapHourly(
            WifiStationHourly(
                id = 9,
                subscriptionId = 2329,
                stationKey = "sta1",
                band = "5",
                bucketStart = at,
                rssiMin = -72.0,
                rssiAvg = -60.0,
                rssiMax = -50.0,
                snrMin = 18.0,
                snrAvg = 22.0,
                sampleCount = 2,
                displayName = "Mac",
            ),
        )
        assertEquals(9L, mapped["reading_id"])
        assertEquals("sta1", mapped["station_key"])
        assertEquals("Mac", mapped["display_name"])
        assertEquals(UtcInstantText.formatApi(at), mapped["observed_at"])
        assertEquals(-72.0, mapped["rssi"])
        assertEquals(18.0, mapped["snr"])
        assertEquals(Quality.FRESH, mapped["quality_status"])
    }

    @Test
    fun `raw points keep the sample rssi`() {
        val at = Instant.parse("2026-08-31T17:30:00Z")
        val mapped = WifiSignalSeries.mapRaw(
            WifiStationSample(
                id = 3,
                countSampleId = 11,
                subscriptionId = 2329,
                stationKey = "sta1",
                band = "2.4",
                displayName = "Phone",
                observedAt = at,
                rssi = -88.0,
                snr = 12.0,
                qualityStatus = Quality.FRESH,
            ),
        )
        assertEquals(11L, mapped["reading_id"])
        assertEquals(-88.0, mapped["rssi"])
        assertEquals("2.4", mapped["band"])
    }
}
