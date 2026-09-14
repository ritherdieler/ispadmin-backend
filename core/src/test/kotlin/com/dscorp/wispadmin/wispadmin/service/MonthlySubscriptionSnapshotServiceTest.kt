package com.dscorp.wispadmin.wispadmin.service

import com.dscorp.wispadmin.wispadmin.data.model.MonthlySubscriptionResume
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionRepository
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionsStaticsRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.time.YearMonth

class MonthlySubscriptionSnapshotServiceTest {

    private val subscriptionRepository = mockk<SubscriptionRepository>()
    private val subscriptionsStaticsRepository = mockk<SubscriptionsStaticsRepository>(relaxed = true)

    private val service = MonthlySubscriptionSnapshotService(
        subscriptionRepository = subscriptionRepository,
        subscriptionsStaticsRepository = subscriptionsStaticsRepository,
    )

    @Test
    fun `calendarMonthRange for July 2026 is first day through next month start`() {
        val (start, end) = service.calendarMonthRange(YearMonth.of(2026, 7))
        assertEquals(LocalDateTime.of(2026, 7, 1, 0, 0), start)
        assertEquals(LocalDateTime.of(2026, 8, 1, 0, 0), end)
    }

    @Test
    fun `computeSnapshot aggregates repository counts`() {
        val start = slot<LocalDateTime>()
        val end = slot<LocalDateTime>()
        every { subscriptionRepository.countNonCancelledSubscriptions() } returns 1200
        every {
            subscriptionRepository.countBySubscriptionDatetimeBetween(capture(start), capture(end))
        } returns 45
        every {
            subscriptionRepository.findQuantityByCancellationDate(capture(start), capture(end))
        } returns 12

        val snapshot = service.computeSnapshot(YearMonth.of(2026, 7))

        assertEquals(1200, snapshot.totalActiveSubscriptions)
        assertEquals(45, snapshot.newSubscriptions)
        assertEquals(12, snapshot.cancelledSubscriptions)
        assertEquals(LocalDateTime.of(2026, 7, 1, 0, 0), start.captured)
        assertEquals(LocalDateTime.of(2026, 8, 1, 0, 0), end.captured)
    }

    @Test
    fun `persistSnapshot saves resume row for month end`() {
        every { subscriptionRepository.countNonCancelledSubscriptions() } returns 10
        every { subscriptionRepository.countBySubscriptionDatetimeBetween(any(), any()) } returns 2
        every { subscriptionRepository.findQuantityByCancellationDate(any(), any()) } returns 1
        every { subscriptionsStaticsRepository.findByDateInRange(any(), any()) } returns emptyList()

        val saved = slot<MonthlySubscriptionResume>()
        every { subscriptionsStaticsRepository.save(capture(saved)) } answers { firstArg() }

        service.persistSnapshot(YearMonth.of(2026, 7))

        verify(exactly = 1) { subscriptionsStaticsRepository.save(any()) }
        assertEquals(10, saved.captured.totalActiveSubscriptions)
        assertEquals(2, saved.captured.newSubscriptions)
        assertEquals(1, saved.captured.cancelledSubscriptions)
    }
}
