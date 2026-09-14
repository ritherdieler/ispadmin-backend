package com.dscorp.wispadmin.wispadmin.service

import com.dscorp.wispadmin.wispadmin.data.model.MonthlySubscriptionResume
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionRepository
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionsStaticsRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.util.Date

@Service
class MonthlySubscriptionSnapshotService(
    private val subscriptionRepository: SubscriptionRepository,
    private val subscriptionsStaticsRepository: SubscriptionsStaticsRepository,
) {

    private val log = LoggerFactory.getLogger(javaClass)
    private val dashboardZone: ZoneId = ZoneId.of("America/Lima")

    data class MonthlySubscriptionSnapshot(
        val totalActiveSubscriptions: Int,
        val newSubscriptions: Int,
        val cancelledSubscriptions: Int,
    )

    fun calendarMonthRange(yearMonth: YearMonth): Pair<LocalDateTime, LocalDateTime> {
        val start = yearMonth.atDay(1).atStartOfDay()
        val end = yearMonth.plusMonths(1).atDay(1).atStartOfDay()
        return start to end
    }

    fun computeSnapshot(yearMonth: YearMonth): MonthlySubscriptionSnapshot {
        val (start, end) = calendarMonthRange(yearMonth)
        val active = subscriptionRepository.countNonCancelledSubscriptions().toInt()
        val newSubs = subscriptionRepository.countBySubscriptionDatetimeBetween(start, end).toInt()
        val cancelled = subscriptionRepository.findQuantityByCancellationDate(start, end)
        return MonthlySubscriptionSnapshot(
            totalActiveSubscriptions = active,
            newSubscriptions = newSubs,
            cancelledSubscriptions = cancelled,
        )
    }

    @Transactional
    fun persistSnapshot(yearMonth: YearMonth) {
        val snapshot = computeSnapshot(yearMonth)
        val (rangeStart, rangeEnd) = resumeDateRange(yearMonth)
        val existing = subscriptionsStaticsRepository.findByDateInRange(rangeStart, rangeEnd)
        if (existing.isNotEmpty()) {
            subscriptionsStaticsRepository.deleteAll(existing)
        }
        subscriptionsStaticsRepository.save(
            MonthlySubscriptionResume(
                id = 0,
                totalActiveSubscriptions = snapshot.totalActiveSubscriptions,
                newSubscriptions = snapshot.newSubscriptions,
                cancelledSubscriptions = snapshot.cancelledSubscriptions,
                date = resumeDate(yearMonth),
            )
        )
        log.info(
            "Monthly subscription snapshot saved for {} active={} new={} cancelled={}",
            yearMonth,
            snapshot.totalActiveSubscriptions,
            snapshot.newSubscriptions,
            snapshot.cancelledSubscriptions,
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
