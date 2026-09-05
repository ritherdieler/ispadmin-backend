package com.dscorp.wispadmin.servicehealth.config

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
    private val properties = ServiceHealthProperties().apply {
        enabled = true
        labSubscriptionIds = setOf(99)
    }

    private fun scope(tag: String): ServiceHealthScope {
        val env = GigafiberEnvironmentProperties().apply { this.tag = tag }
        return ServiceHealthScope(properties, env, subscriptions)
    }

    @Test
    fun `staging collects lab row and skips prod subscriptions`() {
        every { subscriptions.findAllIds() } returns listOf(2310, 2328, 99)
        val staging = scope("stg")
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
