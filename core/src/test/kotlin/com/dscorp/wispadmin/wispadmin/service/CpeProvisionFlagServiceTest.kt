package com.dscorp.wispadmin.wispadmin.service

import com.dscorp.wispadmin.wispadmin.data.model.EquipmentCondition
import com.dscorp.wispadmin.wispadmin.data.model.Onu
import com.dscorp.wispadmin.wispadmin.data.model.Subscription
import com.dscorp.wispadmin.wispadmin.data.model.Tr069ProvisionStatus
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test

class CpeProvisionFlagServiceTest {
    private val subscriptions = mockk<SubscriptionRepository>()
    private val service = CpeProvisionFlagService(subscriptions)

    @Test
    fun `applies COMPLETE when serial maps to one subscription`() {
        val sub = Subscription(
            id = 42,
            fiberOnuSn = "ZTEGDC47BFFD",
            equipmentCondition = EquipmentCondition.values().first(),
        )
        every { subscriptions.findByExactOnuSerial("ZTEGDC47BFFD") } returns listOf(sub)
        every { subscriptions.save(sub) } returns sub
        service.apply("ZTEGDC47BFFD", "COMPLETE")
        verify { subscriptions.save(match { it.tr069ProvisionStatus == Tr069ProvisionStatus.COMPLETE }) }
    }

    @Test
    fun `applies FAILED when serial maps to one subscription`() {
        val sub = Subscription(
            id = 42,
            fiberOnuSn = "ZTEGDC47BFFD",
            equipmentCondition = EquipmentCondition.values().first(),
        )
        every { subscriptions.findByExactOnuSerial("ZTEGDC47BFFD") } returns listOf(sub)
        every { subscriptions.save(sub) } returns sub
        service.apply("ZTEGDC47BFFD", "FAILED")
        verify { subscriptions.save(match { it.tr069ProvisionStatus == Tr069ProvisionStatus.FAILED }) }
    }

    @Test
    fun `ignores ambiguous serial`() {
        every { subscriptions.findByExactOnuSerial("SN") } returns listOf(
            Subscription(id = 1, equipmentCondition = EquipmentCondition.values().first()),
            Subscription(id = 2, equipmentCondition = EquipmentCondition.values().first()),
        )
        service.apply("SN", "FAILED")
        verify(exactly = 0) { subscriptions.save(any()) }
    }
}
