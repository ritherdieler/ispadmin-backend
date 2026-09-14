package com.dscorp.wispadmin.servicehealth

import com.dscorp.wispadmin.servicehealth.domain.OpticalDailySample
import com.dscorp.wispadmin.servicehealth.domain.OpticalSample
import com.dscorp.wispadmin.servicehealth.domain.Quality
import com.dscorp.wispadmin.servicehealth.domain.UtcInstantText
import com.dscorp.wispadmin.servicehealth.service.OpticalSeries
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class OpticalSeriesTest {
    private val to = Instant.parse("2026-09-09T00:00:00Z")

    @Test
    fun `windows within raw max days stay raw`() {
        assertFalse(OpticalSeries.useDaily(to.minusSeconds(90 * 86400), to, 90))
        assertTrue(OpticalSeries.useDaily(to.minusSeconds(90 * 86400 + 1), to, 90))
        assertTrue(OpticalSeries.useDaily(to.minusSeconds(365 * 86400), to, 90))
    }

    @Test
    fun `daily points plot avg rx as onu_rx_dbm and expose min max`() {
        val at = Instant.parse("2026-09-08T00:00:00Z")
        val mapped = OpticalSeries.mapDaily(
            OpticalDailySample(
                id = 9,
                subscriptionId = 2389,
                onuId = 7,
                onuSn = "VSOL1",
                oltId = 1,
                board = 1,
                port = 6,
                onuIndex = 1,
                bucketStart = at,
                onuRxMin = -24.0,
                onuRxAvg = -22.0,
                onuRxMax = -20.0,
                onuTxMin = 2.0,
                onuTxAvg = 2.2,
                onuTxMax = 2.5,
                oltRxMin = -16.0,
                oltRxAvg = -15.0,
                oltRxMax = -14.0,
                sampleCount = 12,
            ),
        )
        assertEquals(9L, mapped["id"])
        assertEquals(UtcInstantText.formatApi(at), mapped["observed_at"])
        assertEquals(-22.0, mapped["onu_rx_dbm"])
        assertEquals(-24.0, mapped["onu_rx_dbm_min"])
        assertEquals(-20.0, mapped["onu_rx_dbm_max"])
        assertEquals(2.2, mapped["onu_tx_dbm"])
        assertEquals(-15.0, mapped["olt_rx_dbm"])
        assertEquals(12, mapped["sample_count"])
        assertEquals(Quality.FRESH, mapped["quality_status"])
    }

    @Test
    fun `raw points keep sample dbm`() {
        val at = Instant.parse("2026-09-08T12:00:00Z")
        val mapped = OpticalSeries.mapRaw(
            OpticalSample(
                id = 3,
                subscriptionId = 2389,
                onuId = 7,
                onuSn = "VSOL1",
                observedAt = at,
                onuRxDbm = -21.5,
                onuTxDbm = 2.1,
                oltRxDbm = -15.2,
                qualityStatus = Quality.FRESH,
            ),
        )
        assertEquals(-21.5, mapped["onu_rx_dbm"])
        assertEquals(2.1, mapped["onu_tx_dbm"])
        assertEquals(-15.2, mapped["olt_rx_dbm"])
    }
}
