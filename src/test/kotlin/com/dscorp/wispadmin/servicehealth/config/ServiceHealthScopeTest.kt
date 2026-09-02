package com.dscorp.wispadmin.servicehealth.config

import com.dscorp.wispadmin.servicehealth.port.AcsRegistryEntry
import com.dscorp.wispadmin.servicehealth.port.AcsSubscriptionPort
import com.dscorp.wispadmin.servicehealth.port.SubscriptionDirectoryPort
import com.dscorp.wispadmin.wispadmin.config.GigafiberEnvironmentProperties
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ServiceHealthScopeTest {

    private val acs = mockk<AcsSubscriptionPort>()
    private val subscriptions = mockk<SubscriptionDirectoryPort>()
    private val properties = ServiceHealthProperties().apply {
        enabled = true
    }

    private fun scope(tag: String): ServiceHealthScope {
        val env = GigafiberEnvironmentProperties().apply { this.tag = tag }
        return ServiceHealthScope(properties, env, acs, subscriptions)
    }

    @Test
    fun `staging collects lab row and skips prod subscriptions`() {
        every { acs.isLab(99) } returns true
        every { acs.isLab(2328) } returns false
        every { acs.labSubscriptionIds() } returns listOf(99)
        every { subscriptions.allIds() } returns listOf(2310, 2328, 99)
        val staging = scope("stg")
        assertTrue(staging.collects(99))
        assertFalse(staging.collects(2328))
        assertEquals(setOf(99), staging.collectionSubscriptionIds())
    }

    @Test
    fun `prod collects all subscriptions and skips lab`() {
        every { acs.isLab(2328) } returns false
        every { acs.isLab(99) } returns true
        every { acs.labSubscriptionIds() } returns listOf(99)
        every { subscriptions.allIds() } returns listOf(2310, 2328, 99)
        val prod = scope("")
        assertTrue(prod.collects(2328))
        assertFalse(prod.collects(99))
        assertEquals(setOf(2310, 2328), prod.collectionSubscriptionIds())
    }
}
