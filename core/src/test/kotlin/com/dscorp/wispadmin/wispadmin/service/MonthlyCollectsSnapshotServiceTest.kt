package com.dscorp.wispadmin.wispadmin.service

import com.dscorp.wispadmin.wispadmin.data.model.MonthlyCollectsResume
import com.dscorp.wispadmin.wispadmin.data.model.toDto
import com.dscorp.wispadmin.wispadmin.repository.MonthlyCollectsRepository
import com.dscorp.wispadmin.wispadmin.repository.PaymentRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.time.YearMonth

class MonthlyCollectsSnapshotServiceTest {

    private val paymentRepository = mockk<PaymentRepository>()
    private val monthlyCollectsRepository = mockk<MonthlyCollectsRepository>(relaxed = true)

    private val service = MonthlyCollectsSnapshotService(
        paymentRepository = paymentRepository,
        monthlyCollectsRepository = monthlyCollectsRepository,
    )

    @Test
    fun `billingCycleRange for July 2026 matches dashboard cycle`() {
        val (start, end) = service.billingCycleRange(YearMonth.of(2026, 7))
        assertEquals(LocalDateTime.of(2026, 6, 30, 0, 0), start)
        assertEquals(LocalDateTime.of(2026, 7, 31, 0, 0), end)
    }

    @Test
    fun `computeSnapshot sets receivables as residual so components sum to gross`() {
        val start = slot<LocalDateTime>()
        val end = slot<LocalDateTime>()
        every { paymentRepository.getGrossRevenueBetween(capture(start), capture(end)) } returns 100.0
        every { paymentRepository.getTotalRaisedBetween(any(), any()) } returns 80.0
        every { paymentRepository.getTotalDiscountsBetween(any(), any()) } returns 5.0

        val snapshot = service.computeSnapshot(YearMonth.of(2026, 7))

        assertEquals(100.0, snapshot.grossIncome)
        assertEquals(80.0, snapshot.totalRaised)
        assertEquals(5.0, snapshot.totalDiscount)
        assertEquals(15.0, snapshot.totalReceivables)
        assertEquals(100.0, snapshot.totalRaised + snapshot.totalDiscount + snapshot.totalReceivables)
    }

    @Test
    fun `toDto percentages sum to 100 when components sum to gross`() {
        val entity = MonthlyCollectsResume(
            id = 0,
            grossIncome = 49202.0,
            totalRaised = 43480.5,
            totalDiscount = 1834.5,
            totalReceivables = 3887.0,
            date = java.util.Date(),
        )
        val dto = entity.toDto()
        val sumPct = dto.totalRaised + dto.totalDiscount + dto.totalReceivables
        assertTrue(sumPct in 99.5..100.5)
    }

    @Test
    fun `persistSnapshot replaces rows for resume month and saves partition of gross`() {
        every { paymentRepository.getGrossRevenueBetween(any(), any()) } returns 200.0
        every { paymentRepository.getTotalRaisedBetween(any(), any()) } returns 150.0
        every { paymentRepository.getTotalDiscountsBetween(any(), any()) } returns 10.0
        every { monthlyCollectsRepository.findByDateInRange(any(), any()) } returns emptyList()

        val saved = slot<MonthlyCollectsResume>()
        every { monthlyCollectsRepository.save(capture(saved)) } answers { firstArg() }

        service.persistSnapshot(YearMonth.of(2026, 7))

        verify(exactly = 1) { monthlyCollectsRepository.save(any()) }
        assertEquals(200.0, saved.captured.grossIncome)
        assertEquals(40.0, saved.captured.totalReceivables)
        verify(exactly = 0) { monthlyCollectsRepository.deleteAll(any()) }
    }
}
