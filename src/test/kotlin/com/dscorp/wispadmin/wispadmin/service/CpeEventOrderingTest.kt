package com.dscorp.wispadmin.wispadmin.service
import com.dscorp.wispadmin.wispadmin.data.model.*
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionRepository
import io.mockk.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.Instant
class CpeEventOrderingTest {
    @Test fun `duplicate and older events cannot overwrite a newer operation state`() {
        val repo=mockk<SubscriptionRepository>()
        val subscription=Subscription(id=7,fiberOnuSn="SN1",equipmentCondition=EquipmentCondition.values().first())
        every { repo.findByExactOnuSerial("SN1") } returns listOf(subscription)
        every { repo.lockIdentityOwner(7) } returns subscription
        every { repo.save(any()) } answers { firstArg() }
        val service=CpeProvisionFlagService(repo)
        val at=Instant.parse("2026-09-05T12:00:00Z")
        service.apply("SN1","COMPLETE",at,"event-2")
        service.apply("SN1","PENDING",at.minusSeconds(1),"event-1")
        service.apply("SN1","COMPLETE",at,"event-2")
        assertEquals(Tr069ProvisionStatus.COMPLETE,subscription.tr069ProvisionStatus)
        verify(exactly=1) { repo.save(any()) }
        service.apply("SN1","PENDING",at.plusSeconds(1),"new-operation")
        assertEquals(Tr069ProvisionStatus.PENDING,subscription.tr069ProvisionStatus)
    }
}
