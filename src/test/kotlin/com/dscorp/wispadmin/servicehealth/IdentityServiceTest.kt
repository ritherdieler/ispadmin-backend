package com.dscorp.wispadmin.servicehealth

import com.dscorp.wispadmin.servicehealth.service.IdentityService
import com.dscorp.wispadmin.servicehealth.repository.*
import com.dscorp.wispadmin.wispadmin.repository.*
import com.dscorp.wispadmin.wispadmin.data.model.Subscription
import com.dscorp.wispadmin.wispadmin.data.model.SubscriptionAcs
import com.dscorp.wispadmin.oltgateway.domain.repository.OltMgrOnuRepository
import com.dscorp.wispadmin.traffic.repository.SubscriptionTrafficSampleRepository
import com.fasterxml.jackson.databind.ObjectMapper
import com.dscorp.wispadmin.wispadmin.data.model.EquipmentCondition
import io.mockk.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.Optional

class IdentityServiceTest {
    private val subscriptions=mockk<SubscriptionRepository>()
    private val acs=mockk<SubscriptionAcsRepository>()
    private val service=IdentityService(subscriptions,acs,mockk<OltMgrOnuRepository>(),mockk<IdentityLinkRepository>(),
        mockk<IdentityConflictRepository>(relaxed=true),mockk<SubscriptionTrafficSampleRepository>(),ObjectMapper())
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
}
