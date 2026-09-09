package com.dscorp.wispadmin.servicehealth.config

import com.dscorp.wispadmin.servicehealth.port.AcsSubscriptionPort
import com.dscorp.wispadmin.wispadmin.config.GigafiberEnvironmentProperties
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionRepository
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ServiceHealthScopeTest {

    private val subscriptions = mockk<SubscriptionRepository>()
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
        return ServiceHealthScope(properties, env, subscriptions, acs)
    }

    @Test
    fun `staging collects lab row and skips prod subscriptions`() {
        every { subscriptions.findAllIds() } returns listOf(2310, 2328, 99)
        val staging = scope("stg")
        assertTrue(staging.lab(99))
        assertFalse(staging.lab(2328))
        assertTrue(staging.collects(99))
        assertFalse(staging.collects(2328))
        assertEquals(setOf(99), staging.collectionSubscriptionIds())
    }

    @Test
    fun `prod collects all subscriptions and skips lab`() {
        every { subscriptions.findAllIds() } returns listOf(2310, 2328, 99)
        val prod = scope("")
        assertTrue(prod.collects(2328))
        assertFalse(prod.collects(99))
        assertEquals(setOf(2310, 2328), prod.collectionSubscriptionIds())
    }
}
