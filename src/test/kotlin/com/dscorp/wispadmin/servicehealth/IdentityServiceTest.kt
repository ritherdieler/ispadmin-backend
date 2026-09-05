package com.dscorp.wispadmin.servicehealth

import com.dscorp.wispadmin.servicehealth.repository.*
import com.dscorp.wispadmin.servicehealth.port.HealthOnuPort
import com.dscorp.wispadmin.servicehealth.port.HealthTrafficPort
import com.dscorp.wispadmin.servicehealth.service.IdentityService
import com.dscorp.wispadmin.wispadmin.data.model.EquipmentCondition
import com.dscorp.wispadmin.wispadmin.data.model.Subscription
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionRepository
import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.ObjectProvider
import java.util.Optional

class IdentityServiceTest {
    private val subscriptions=mockk<SubscriptionRepository>()
    private val service=IdentityService(subscriptions,emptyProvider<HealthOnuPort>(),mockk<IdentityLinkRepository>(),
        mockk<IdentityConflictRepository>(relaxed=true),emptyProvider<HealthTrafficPort>(),ObjectMapper())

    private fun <T : Any> emptyProvider(): ObjectProvider<T> {
        val provider = mockk<ObjectProvider<T>>()
        every { provider.ifAvailable } returns null
        return provider
    }
    @Test fun `canonical device collision is rejected`() {
        every { subscriptions.findByTr069DeviceId("d") } returns listOf(
            Subscription(id=1,tr069DeviceId="d",equipmentCondition=EquipmentCondition.values().first()),
            Subscription(id=2,tr069DeviceId="d",equipmentCondition=EquipmentCondition.values().first()),
        )
        assertNull(service.resolveAcs("d"))
    }
    @Test fun `operational fallback is accepted only when canonical data does not contradict it`() {
        every { subscriptions.findByTr069DeviceId("d") } returns listOf(Subscription(id=1,tr069DeviceId="d",equipmentCondition=EquipmentCondition.values().first()))
        every { subscriptions.findById(1) } returns Optional.of(Subscription(id=1,tr069DeviceId="d",equipmentCondition=EquipmentCondition.values().first()))
        assertEquals(1,service.resolveAcs("d"))
        every { subscriptions.findById(1) } returns Optional.of(Subscription(id=1,tr069DeviceId="another-device",equipmentCondition=EquipmentCondition.values().first()))
        assertNull(service.resolveAcs("d"))
    }
    @Test fun `unmapped ACS devices cannot manufacture a subscription from tags or serial`() {
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
