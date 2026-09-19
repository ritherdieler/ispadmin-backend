package com.dscorp.wispadmin.wispadmin.service

import com.dscorp.wispadmin.wispadmin.data.model.EquipmentCondition
import com.dscorp.wispadmin.wispadmin.data.model.ServiceStatus
import com.dscorp.wispadmin.wispadmin.data.model.Subscription
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionRepository
import com.dscorp.wispadmin.wispadmin.search.application.SubscriptionChangedEvent
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationEventPublisher

class FiberOnuSnClaimServiceTest {

    private val repository = mockk<SubscriptionRepository>()
    private val events = mockk<ApplicationEventPublisher>(relaxed = true)
    private val service = FiberOnuSnClaimService(repository, events)

    @Test
    fun `blank serial is a no-op`() {
        service.claim("  ")
        verify(exactly = 0) { repository.findByOnuSerialOrSuffix(any(), any()) }
        verify(exactly = 0) { repository.findByExactOnuSerial(any()) }
    }

    @Test
    fun `releases cancelled holder including tr069 device id`() {
        val cancelled = holder(1783, "VSOL0086F109", ServiceStatus.CANCELLED).apply {
            tr069DeviceId = "B46415-V2804AX15T-12345B4641586F109"
        }
        every { repository.findByOnuSerialOrSuffix("VSOL0086F109", "86F109") } returns listOf(cancelled)
        every { repository.save(cancelled) } returns cancelled

        service.claim("VSOL0086F109")

        assertNull(cancelled.fiberOnuSn)
        assertNull(cancelled.tr069DeviceId)
        verify { repository.save(cancelled) }
        verify { events.publishEvent(SubscriptionChangedEvent(1783)) }
    }

    @Test
    fun `releases cancelled holder matched by hex suffix`() {
        val cancelled = holder(1852, "HWTC15F5C4F6", ServiceStatus.CANCELLED)
        every { repository.findByOnuSerialOrSuffix("VSOL00F5C4F6", "F5C4F6") } returns listOf(cancelled)
        every { repository.save(cancelled) } returns cancelled

        service.claim("VSOL00F5C4F6")

        assertNull(cancelled.fiberOnuSn)
        verify { repository.save(cancelled) }
    }

    @Test
    fun `rejects when another ACTIVE subscription holds the serial`() {
        val active = holder(1808, "VSOL0086D819", ServiceStatus.ACTIVE)
        every { repository.findByOnuSerialOrSuffix("VSOL0086D819", "86D819") } returns listOf(active)

        val ex = assertThrows(IllegalStateException::class.java) {
            service.claim("VSOL0086D819")
        }

        assertTrue(ex.message!!.contains("1808"))
        assertTrue(ex.message!!.contains("ACTIVE"))
        verify(exactly = 0) { repository.save(any()) }
    }

    @Test
    fun `rejects CUT_OFF and SUSPENDED holders`() {
        every { repository.findByOnuSerialOrSuffix("VSOL0086D819", "86D819") } returns listOf(
            holder(11, "VSOL0086D819", ServiceStatus.CUT_OFF),
        )
        assertThrows(IllegalStateException::class.java) { service.claim("VSOL0086D819") }

        every { repository.findByOnuSerialOrSuffix("VSOL0086D819", "86D819") } returns listOf(
            holder(12, "VSOL0086D819", ServiceStatus.SUSPENDED),
        )
        assertThrows(IllegalStateException::class.java) { service.claim("VSOL0086D819") }
        verify(exactly = 0) { repository.save(any()) }
    }

    @Test
    fun `does not release cancelled when an ACTIVE holder exists`() {
        val active = holder(2070, "VSOL0086D819", ServiceStatus.ACTIVE)
        val cancelled = holder(1808, "VSOL0086D819", ServiceStatus.CANCELLED)
        every { repository.findByOnuSerialOrSuffix("VSOL0086D819", "86D819") } returns listOf(active, cancelled)

        assertThrows(IllegalStateException::class.java) { service.claim("VSOL0086D819") }
        assertEquals("VSOL0086D819", cancelled.fiberOnuSn)
        verify(exactly = 0) { repository.save(any()) }
    }

    @Test
    fun `ignores the subscription being migrated`() {
        val self = holder(99, "VSOL0086D819", ServiceStatus.ACTIVE)
        every { repository.findByOnuSerialOrSuffix("VSOL0086D819", "86D819") } returns listOf(self)

        service.claim("VSOL0086D819", excludingSubscriptionId = 99)

        verify(exactly = 0) { repository.save(any()) }
    }

    @Test
    fun `releases every cancelled holder`() {
        val first = holder(1, "VSOL0086F109", ServiceStatus.CANCELLED)
        val second = holder(2, "HWTC0086F109", ServiceStatus.CANCELLED)
        every { repository.findByOnuSerialOrSuffix("VSOL0086F109", "86F109") } returns listOf(first, second)
        every { repository.save(any()) } answers { firstArg() }

        service.claim("VSOL0086F109")

        assertNull(first.fiberOnuSn)
        assertNull(second.fiberOnuSn)
        verify(exactly = 2) { repository.save(any()) }
    }

    private fun holder(id: Int, sn: String, status: ServiceStatus) = Subscription(
        id = id,
        fiberOnuSn = sn,
        equipmentCondition = EquipmentCondition.LOAN,
        serviceStatus = status,
    )
}
