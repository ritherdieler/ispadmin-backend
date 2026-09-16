package com.dscorp.wispadmin.servicehealth.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ServiceHealthPropertiesTest {

    @Test
    fun `module flags stay independent of any collection list`() {
        val p = ServiceHealthProperties()
        assertFalse(p.enabled)
        assertFalse(p.opticalEnabled)
        assertFalse(p.acsEnabled)
        assertFalse(p.correlationEnabled)
        assertFalse(p.actionsEnabled)
        p.enabled = true
        p.opticalEnabled = true
        p.acsEnabled = true
        assertTrue(p.enabled)
        assertTrue(p.opticalEnabled)
        assertTrue(p.acsEnabled)
    }

    @Test
    fun `wifi sample cadence defaults to 30 minutes and freshness to an hour`() {
        val p = ServiceHealthProperties()
        assertEquals(1800L, p.acsWifiSampleTargetSeconds)
        assertEquals(3600L, p.wifiSampleFreshSeconds())
        assertEquals(1800L, p.periodicInformSeconds, "must track PeriodicInformInterval in gigafiber-bootstrap.js")
        assertEquals(7L, p.stationSeriesRawMaxDays)
        assertEquals(90L, p.stationHourlyRetentionDays)
        assertEquals(14L, p.stationRetentionDays)
        assertEquals(90L, p.opticalRetentionDays)
        assertEquals(730L, p.opticalDailyRetentionDays)
        assertEquals(90L, p.opticalSeriesRawMaxDays)
    }

    @Test
    fun `staging wifi sample cadence is three minutes`() {
        val p = ServiceHealthProperties().apply {
            acsWifiSampleTargetSeconds = 180
        }
        assertEquals(180L, p.acsWifiSampleTargetSeconds)
        assertEquals(360L, p.wifiSampleFreshSeconds())
    }
}
