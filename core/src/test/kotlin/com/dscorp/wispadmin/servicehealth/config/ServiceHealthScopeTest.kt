package com.dscorp.wispadmin.servicehealth.config

import com.dscorp.wispadmin.servicehealth.port.AcsSubscriptionPort
import com.dscorp.wispadmin.servicehealth.port.SubscriptionDirectoryPort
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ServiceHealthScopeTest {

    private val directory = mockk<SubscriptionDirectoryPort>()
    private val acs = mockk<AcsSubscriptionPort>()

    private fun scope(): ServiceHealthScope {
        every { acs.isLab(99) } returns true
        every { acs.isLab(2310) } returns false
        every { acs.isLab(2328) } returns false
        every { acs.isLab(null) } returns false
        return ServiceHealthScope(directory, acs)
    }

    @Test
    fun `lab follows genieacs tag and evaluation includes every directory id`() {
        every { directory.allIds() } returns listOf(2310, 2328, 99)
        val scope = scope()
        assertTrue(scope.lab(99))
        assertFalse(scope.lab(2328))
        assertFalse(scope.lab(2310))
        assertEquals(setOf(2310, 2328, 99), scope.collectionSubscriptionIds())
    }
}
