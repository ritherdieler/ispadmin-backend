package com.dscorp.wispadmin.servicehealth

import com.dscorp.wispadmin.servicehealth.service.IdentityService
import com.dscorp.wispadmin.servicehealth.repository.*
import com.dscorp.wispadmin.servicehealth.port.HealthOnuPort
import com.dscorp.wispadmin.servicehealth.port.HealthTrafficPort
import com.dscorp.wispadmin.wispadmin.repository.*
import com.dscorp.wispadmin.wispadmin.data.model.Subscription
import com.dscorp.wispadmin.wispadmin.data.model.SubscriptionAcs
import com.fasterxml.jackson.databind.ObjectMapper
import com.dscorp.wispadmin.wispadmin.data.model.EquipmentCondition
import io.mockk.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.ObjectProvider
import java.util.Optional

class IdentityServiceTest {
    private val subscriptions=mockk<SubscriptionRepository>()
    private val acs=mockk<SubscriptionAcsRepository>()
    private val service=IdentityService(subscriptions,acs,emptyProvider<HealthOnuPort>(),mockk<IdentityLinkRepository>(),
        mockk<IdentityConflictRepository>(relaxed=true),emptyProvider<HealthTrafficPort>(),ObjectMapper())

    private fun <T : Any> emptyProvider(): ObjectProvider<T> {
        val provider = mockk<ObjectProvider<T>>()
        every { provider.ifAvailable } returns null
        return provider
    }
    @Test fun `canonical device collision is rejected`() {
        every { acs.findByGenieacsDeviceId("d") } returns listOf(SubscriptionAcs(subscriptionId=1),SubscriptionAcs(subscriptionId=2))
        every { subscriptions.findByTr069DeviceId("d") } returns emptyList()
        assertNull(service.resolveAcs("d"))
    }
    @Test fun `operational fallback is accepted only when canonical data does not contradict it`() {
        every { acs.findByGenieacsDeviceId("d") } returns emptyList()
        every { subscriptions.findByTr069DeviceId("d") } returns listOf(Subscription(id=1,tr069DeviceId="d",equipmentCondition=EquipmentCondition.values().first()))
        every { subscriptions.findById(1) } returns Optional.of(Subscription(id=1,tr069DeviceId="d",equipmentCondition=EquipmentCondition.values().first()))
        every { acs.findById(1) } returns Optional.empty()
        assertEquals(1,service.resolveAcs("d"))
        every { acs.findById(1) } returns Optional.of(SubscriptionAcs(subscriptionId=1,genieacsDeviceId="another-device"))
        assertNull(service.resolveAcs("d"))
    }
    @Test fun `unmapped ACS devices cannot manufacture a subscription from tags or serial`() {
        every { acs.findByGenieacsDeviceId(any()) } returns emptyList()
        every { subscriptions.findByTr069DeviceId(any()) } returns emptyList()
        assertNull(service.resolveAcs("sub-1-device"))
        verify(exactly=0) { subscriptions.findById(any()) }
    }

    @Test fun `resolveOnu maps VSOL inventory SN to Genie fiber serial via hex suffix`() {
        every { subscriptions.findByExactOnuSerial("VSOL0031C0B6") } returns emptyList()
        every { subscriptions.findByOnuSerialOrSuffix("VSOL0031C0B6", "31C0B6") } returns listOf(
            Subscription(id = 2329, equipmentCondition = EquipmentCondition.values().first())
        )
        assertEquals(2329, service.resolveOnu("VSOL0031C0B6"))
    }
}
