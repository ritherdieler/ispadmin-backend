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
    fun `staging collects only lab regardless of env pilot list`() {
        val p = props().apply { pilotSubscriptionIds = setOf(2310, 2328) }
        assertTrue(p.collects(99, lab = true, environmentTag = "stg"))
        assertFalse(p.collects(2328, lab = false, environmentTag = "stg"))
        assertFalse(p.collects(2310, lab = false, environmentTag = "STG"))
    }

    @Test
    fun `disabled never collects`() {
        val p = props().apply { enabled = false }
        assertFalse(p.collects(99, lab = true, environmentTag = "stg"))
        assertFalse(p.collects(2328, lab = false, environmentTag = ""))
    }

    @Test
    fun `collection ids in staging are lab rows only`() {
        val p = props()
        assertEquals(setOf(77), p.collectionSubscriptionIds(listOf(77), "stg", listOf(10, 20, 77)))
        assertEquals(setOf(10, 20), p.collectionSubscriptionIds(listOf(77), "", listOf(10, 20, 77)))
        val restricted = props().apply { pilotSubscriptionIds = setOf(2310, 2328) }
        assertEquals(setOf(2310), restricted.collectionSubscriptionIds(listOf(2328), "", listOf(10, 20, 77)))
    }

    @Test
    fun `wifi sample cadence defaults to 30 minutes and freshness to an hour`() {
        val p = ServiceHealthProperties()
        assertEquals(1800L, p.acsWifiSampleTargetSeconds)
        assertEquals(1800L, p.acsGpvCooldownSeconds)
        assertEquals(3600L, p.wifiSampleFreshSeconds())
        assertEquals(7L, p.stationSeriesRawMaxDays)
        assertEquals(90L, p.stationHourlyRetentionDays)
        assertEquals(14L, p.stationRetentionDays)
    }

    @Test
    fun `staging wifi sample cadence is three minutes`() {
        val p = ServiceHealthProperties().apply {
            acsWifiSampleTargetSeconds = 180
            acsGpvCooldownSeconds = 180
        }
        assertEquals(180L, p.acsWifiSampleTargetSeconds)
        assertEquals(180L, p.acsGpvCooldownSeconds)
        assertEquals(360L, p.wifiSampleFreshSeconds())
    }
}
