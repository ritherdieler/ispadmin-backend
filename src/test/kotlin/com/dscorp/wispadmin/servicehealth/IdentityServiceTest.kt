package com.dscorp.wispadmin.servicehealth

import com.dscorp.wispadmin.servicehealth.port.AcsSubscriptionPort
import com.dscorp.wispadmin.servicehealth.port.HealthOnuPort
import com.dscorp.wispadmin.servicehealth.port.HealthTrafficPort
import com.dscorp.wispadmin.servicehealth.port.SubscriptionDirectoryPort
import com.dscorp.wispadmin.servicehealth.repository.IdentityConflictRepository
import com.dscorp.wispadmin.servicehealth.repository.IdentityLinkRepository
import com.dscorp.wispadmin.servicehealth.service.IdentityService
import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.ObjectProvider

class IdentityServiceTest {
    private val subscriptions = mockk<SubscriptionDirectoryPort>()
    private val acs = mockk<AcsSubscriptionPort>()
    private val service = IdentityService(
        subscriptions,
        acs,
        emptyProvider<HealthOnuPort>(),
        mockk<IdentityLinkRepository>(),
        mockk<IdentityConflictRepository>(relaxed = true),
        emptyProvider<HealthTrafficPort>(),
        ObjectMapper()
    )

    private fun <T : Any> emptyProvider(): ObjectProvider<T> {
        val provider = mockk<ObjectProvider<T>>()
        every { provider.ifAvailable } returns null
        return provider
    }

    @Test
    fun `canonical device collision is rejected`() {
        every { acs.findSubscriptionIdsByDeviceId("d") } returns listOf(1, 2)
        assertNull(service.resolveAcs("d"))
    }

    @Test
    fun `subscription_acs is the only ACS source`() {
        every { acs.findSubscriptionIdsByDeviceId("d") } returns listOf(1)
        every { acs.findDeviceId(1) } returns "d"
        assertEquals(1, service.resolveAcs("d"))

        every { acs.findDeviceId(1) } returns "another-device"
        assertNull(service.resolveAcs("d"))
    }

    @Test
    fun `unmapped ACS devices cannot manufacture a subscription from tags or serial`() {
        every { acs.findSubscriptionIdsByDeviceId(any()) } returns emptyList()
        assertNull(service.resolveAcs("sub-1-device"))
        verify(exactly = 0) { subscriptions.find(any()) }
    }

    @Test
    fun `resolveOnu maps VSOL inventory SN to Genie fiber serial via hex suffix`() {
        every { subscriptions.findIdsByOnuSerial("VSOL0031C0B6") } returns emptyList()
        every { subscriptions.findIdsByOnuSerialOrSuffix("VSOL0031C0B6", "31C0B6") } returns listOf(2329)
        assertEquals(2329, service.resolveOnu("VSOL0031C0B6"))
    }
}
