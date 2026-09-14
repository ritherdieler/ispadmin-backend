package com.dscorp.wispadmin.wispadmin.service

import com.dscorp.wispadmin.wispadmin.data.model.MonthlyCollectsResume
import com.dscorp.wispadmin.wispadmin.repository.MonthlyCollectsRepository
import com.dscorp.wispadmin.wispadmin.repository.PaymentRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.util.Date

@Service
class MonthlyCollectsSnapshotService(
    private val paymentRepository: PaymentRepository,
    private val monthlyCollectsRepository: MonthlyCollectsRepository,
) {

    private val log = LoggerFactory.getLogger(javaClass)
    private val dashboardZone: ZoneId = ZoneId.of("America/Lima")

    data class MonthlyCollectsSnapshot(
        val grossIncome: Double,
        val totalRaised: Double,
        val totalDiscount: Double,
        val totalReceivables: Double,
    ) {
        init {
            require(grossIncome >= 0)
            require(totalRaised >= 0)
            require(totalDiscount >= 0)
            require(totalReceivables >= 0)
            val sum = totalRaised + totalDiscount + totalReceivables
            require(kotlin.math.abs(grossIncome - sum) < 0.01) {
                "Snapshot must partition gross: gross=$grossIncome sum=$sum"
            }
        }
    }

    fun billingCycleRange(yearMonth: YearMonth): Pair<LocalDateTime, LocalDateTime> {
        val cycleStart = yearMonth.atDay(1).atStartOfDay().minusDays(1)
        val cycleEnd = yearMonth.plusMonths(1).atDay(1).atStartOfDay().minusDays(1)
        return cycleStart to cycleEnd
    }

    fun computeSnapshot(yearMonth: YearMonth): MonthlyCollectsSnapshot {
        val (start, end) = billingCycleRange(yearMonth)
        val gross = paymentRepository.getGrossRevenueBetween(start, end)
        val raised = paymentRepository.getTotalRaisedBetween(start, end)
        val discount = paymentRepository.getTotalDiscountsBetween(start, end)
        val receivables = (gross - raised - discount).coerceAtLeast(0.0)
        return MonthlyCollectsSnapshot(
            grossIncome = gross,
            totalRaised = raised,
            totalDiscount = discount,
            totalReceivables = receivables,
        )
    }

    @Transactional
    fun persistSnapshot(yearMonth: YearMonth) {
        val snapshot = computeSnapshot(yearMonth)
        val (rangeStart, rangeEnd) = resumeDateRange(yearMonth)
        val existing = monthlyCollectsRepository.findByDateInRange(rangeStart, rangeEnd)
        if (existing.isNotEmpty()) {
            monthlyCollectsRepository.deleteAll(existing)
        }
        val resumeDate = resumeDate(yearMonth)
        monthlyCollectsRepository.save(
            MonthlyCollectsResume(
                id = 0,
                grossIncome = snapshot.grossIncome,
                totalRaised = snapshot.totalRaised,
                totalDiscount = snapshot.totalDiscount,
                totalReceivables = snapshot.totalReceivables,
                date = resumeDate,
            )
        )
        log.info(
            "Monthly collects snapshot saved for {} gross={} raised={} discount={} receivables={}",
            yearMonth,
            snapshot.grossIncome,
            snapshot.totalRaised,
            snapshot.totalDiscount,
            snapshot.totalReceivables,
        )
    }

    fun resumeDate(yearMonth: YearMonth): Date {
        val instant = yearMonth.atEndOfMonth().atStartOfDay(dashboardZone).toInstant()
        return Date.from(instant)
    }

    private fun resumeDateRange(yearMonth: YearMonth): Pair<Date, Date> {
        val start = resumeDate(yearMonth)
        val endInstant = yearMonth.atEndOfMonth().plusDays(1).atStartOfDay(dashboardZone).toInstant()
        return start to Date.from(endInstant)
    }
}
