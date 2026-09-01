package com.dscorp.wispadmin.servicehealth.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ServiceHealthPropertiesTest {

    private fun props() = ServiceHealthProperties().apply {
        enabled = true
        pilotSubscriptionIds = setOf(2310, 2328)
    }

    @Test
    fun `prod collects pilot and skips lab`() {
        val p = props()
        assertTrue(p.collects(2328, lab = false, environmentTag = ""))
        assertFalse(p.collects(2328, lab = true, environmentTag = ""))
        assertFalse(p.collects(99, lab = false, environmentTag = ""))
        assertFalse(p.collects(99, lab = true, environmentTag = ""))
    }

    @Test
    fun `staging collects only lab regardless of env pilot list`() {
        val p = props()
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
        assertEquals(setOf(77), p.collectionSubscriptionIds(listOf(77), "stg"))
        assertEquals(setOf(2310, 2328), p.collectionSubscriptionIds(emptyList(), ""))
        assertEquals(setOf(2310), p.collectionSubscriptionIds(listOf(2328), ""))
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
}
