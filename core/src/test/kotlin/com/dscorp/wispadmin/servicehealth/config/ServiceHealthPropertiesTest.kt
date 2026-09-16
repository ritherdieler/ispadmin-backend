package com.dscorp.wispadmin.servicehealth.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ServiceHealthPropertiesTest {

    private fun props() = ServiceHealthProperties().apply {
        enabled = true
    }

    @Test
    fun `prod collects all non-lab when pilot list is empty`() {
        val p = props()
        assertTrue(p.collects(2328, lab = false, environmentTag = ""))
        assertTrue(p.collects(99, lab = false, environmentTag = ""))
        assertFalse(p.collects(99, lab = true, environmentTag = ""))
    }

    @Test
    fun `prod restricts to pilot list when configured`() {
        val p = props().apply { pilotSubscriptionIds = setOf(2310, 2328) }
        assertTrue(p.collects(2328, lab = false, environmentTag = ""))
        assertFalse(p.collects(99, lab = false, environmentTag = ""))
    }

    @Test
    fun `staging collects every subscription so e2e stays on`() {
        val p = props().apply { pilotSubscriptionIds = setOf(2310, 2328) }
        assertTrue(p.collects(99, lab = true, environmentTag = "stg"))
        assertTrue(p.collects(2328, lab = false, environmentTag = "stg"))
        assertTrue(p.collects(2310, lab = false, environmentTag = "STG"))
        assertTrue(p.collects(10, lab = false, environmentTag = "stg"))
        assertFalse(p.collects(null, lab = false, environmentTag = "stg"))
    }

    @Test
    fun `prestaging and any tagged env collect every subscription`() {
        val p = props()
        assertTrue(p.collects(99, lab = true, environmentTag = "lpstg"))
        assertTrue(p.collects(6, lab = false, environmentTag = "lpstg"))
        assertTrue(p.collects(99, lab = true, environmentTag = "dev"))
        assertTrue(p.collects(6, lab = false, environmentTag = "dev"))
    }

    @Test
    fun `disabled tagged env still collects every subscription`() {
        val p = props().apply { enabled = false }
        assertTrue(p.collects(99, lab = true, environmentTag = "stg"))
        assertTrue(p.collects(99, lab = true, environmentTag = "lpstg"))
        assertTrue(p.collects(6, lab = false, environmentTag = "stg"))
        assertTrue(p.collects(10, lab = false, environmentTag = "stg"))
        assertFalse(p.collects(2328, lab = false, environmentTag = ""))
        assertFalse(p.collects(99, lab = true, environmentTag = ""))
    }

    @Test
    fun `collection ids in staging are every subscription`() {
        val p = props()
        assertEquals(setOf(10, 20, 77), p.collectionSubscriptionIds(listOf(77), "stg", listOf(10, 20, 77)))
        assertEquals(setOf(10, 20), p.collectionSubscriptionIds(listOf(77), "", listOf(10, 20, 77)))
        val restricted = props().apply { pilotSubscriptionIds = setOf(2310, 2328) }
        assertEquals(setOf(2310), restricted.collectionSubscriptionIds(listOf(2328), "", listOf(10, 20, 77)))
    }

    @Test
    fun `disabled tagged env still returns every collection id`() {
        val p = props().apply { enabled = false }
        assertEquals(setOf(10, 20, 77), p.collectionSubscriptionIds(listOf(77), "stg", listOf(10, 20, 77)))
        assertEquals(setOf(5, 6, 7), p.collectionSubscriptionIds(listOf(5, 6), "lpstg", listOf(5, 6, 7)))
        assertEquals(emptySet<Int>(), p.collectionSubscriptionIds(listOf(77), "", listOf(10, 20, 77)))
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
