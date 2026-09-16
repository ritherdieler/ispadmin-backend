package com.dscorp.wispadmin.servicehealth.config

import com.dscorp.wispadmin.servicehealth.port.AcsSubscriptionPort
import com.dscorp.wispadmin.servicehealth.port.SubscriptionDirectoryPort
import com.dscorp.wispadmin.shared.config.GigafiberEnvironmentProperties
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ServiceHealthScopeTest {

    private val directory = mockk<SubscriptionDirectoryPort>()
    private val acs = mockk<AcsSubscriptionPort>()
    private val properties = ServiceHealthProperties().apply {
        enabled = true
    }

    private fun scope(tag: String): ServiceHealthScope {
        val env = GigafiberEnvironmentProperties().apply { this.tag = tag }
        every { acs.isLab(99) } returns true
        every { acs.isLab(2310) } returns false
        every { acs.isLab(2328) } returns false
        every { acs.isLab(null) } returns false
        every { acs.labSubscriptionIds() } returns listOf(99)
        return ServiceHealthScope(properties, env, directory, acs)
    }

    @Test
    fun `staging collects every subscription including non-lab e2e`() {
        every { directory.allIds() } returns listOf(2310, 2328, 99)
        val staging = scope("stg")
        assertTrue(staging.lab(99))
        assertFalse(staging.lab(2328))
        assertTrue(staging.collects(99))
        assertTrue(staging.collects(2328))
        assertTrue(staging.collects(2310))
        assertEquals(setOf(2310, 2328, 99), staging.collectionSubscriptionIds())
    }

    @Test
    fun `prod collects all subscriptions and skips lab`() {
        every { directory.allIds() } returns listOf(2310, 2328, 99)
        val prod = scope("")
        assertTrue(prod.collects(2328))
        assertFalse(prod.collects(99))
        assertEquals(setOf(2310, 2328), prod.collectionSubscriptionIds())
    }

    @Test
    fun `prestaging collects every subscription`() {
        every { directory.allIds() } returns listOf(2310, 2328, 99)
        val prestaging = scope("lpstg")
        assertTrue(prestaging.collects(99))
        assertTrue(prestaging.collects(2310))
        assertEquals(setOf(2310, 2328, 99), prestaging.collectionSubscriptionIds())
    }

    @Test
    fun `tagged env keeps every collection id when health enabled is off`() {
        properties.enabled = false
        every { directory.allIds() } returns listOf(2310, 2328, 99)
        val staging = scope("stg")
        assertTrue(staging.collects(99))
        assertTrue(staging.collects(2328))
        assertEquals(setOf(2310, 2328, 99), staging.collectionSubscriptionIds())
        val prod = scope("")
        assertFalse(prod.collects(99))
        assertFalse(prod.collects(2328))
        assertEquals(emptySet<Int>(), prod.collectionSubscriptionIds())
    }
}
