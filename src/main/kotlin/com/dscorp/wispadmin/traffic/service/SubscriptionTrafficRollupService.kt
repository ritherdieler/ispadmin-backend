package com.dscorp.wispadmin.traffic.service

import com.dscorp.wispadmin.traffic.config.TrafficProperties
import com.dscorp.wispadmin.traffic.entity.SubscriptionTrafficDaily
import com.dscorp.wispadmin.traffic.entity.SubscriptionTrafficHourly
import com.dscorp.wispadmin.traffic.entity.SubscriptionTrafficMonthly
import com.dscorp.wispadmin.traffic.repository.NetworkTrafficHourOfDayRepository
import com.dscorp.wispadmin.traffic.repository.SubscriptionTrafficDailyRepository
import com.dscorp.wispadmin.traffic.repository.SubscriptionTrafficHourlyRepository
import com.dscorp.wispadmin.traffic.repository.SubscriptionTrafficMonthlyRepository
import com.dscorp.wispadmin.traffic.repository.SubscriptionTrafficSampleRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import kotlin.math.ceil

@Service
open class SubscriptionTrafficRollupService(
    private val sampleRepository: SubscriptionTrafficSampleRepository,
    private val hourlyRepository: SubscriptionTrafficHourlyRepository,
    private val dailyRepository: SubscriptionTrafficDailyRepository,
    private val monthlyRepository: SubscriptionTrafficMonthlyRepository
) {
    companion object {
        private val logger = LoggerFactory.getLogger(SubscriptionTrafficRollupService::class.java)
    }

    @Transactional
    open fun rollupAll() {
        val now = LocalDateTime.now()
        rollupHourly(now.minusHours(2), now)
        rollupDaily(now.toLocalDate().minusDays(1))
        rollupMonthly(YearMonth.from(now))
        logger.info("Traffic rollup completed")
    }

    @Transactional
    open fun rollupHourly(from: LocalDateTime, to: LocalDateTime) {
        val samples = sampleRepository.findAllInBucketRange(from, to)
        if (samples.isEmpty()) return
        samples.groupBy { it.subscriptionId to truncateHour(it.bucketStart) }.forEach { (key, group) ->
            val (subscriptionId, bucketStart) = key
            val hourly = hourlyRepository.findBySubscriptionIdAndBucketStart(subscriptionId, bucketStart)
                ?: SubscriptionTrafficHourly(subscriptionId = subscriptionId, bucketStart = bucketStart)
            hourly.rxBytesTotal = group.sumOf { it.rxBytesDelta }
            hourly.txBytesTotal = group.sumOf { it.txBytesDelta }
            hourly.maxMbpsDown = group.maxOfOrNull { it.avgMbpsDown } ?: 0.0
            hourly.maxMbpsUp = group.maxOfOrNull { it.avgMbpsUp } ?: 0.0
            hourly.p95MbpsDown = percentile(group.map { it.avgMbpsDown }, 0.95)
            hourly.p95MbpsUp = percentile(group.map { it.avgMbpsUp }, 0.95)
            hourly.sampleCount = group.size
            hourlyRepository.save(hourly)
        }
    }

    @Transactional
    open fun rollupDaily(day: LocalDate) {
        val from = day.atStartOfDay()
        val to = day.plusDays(1).atStartOfDay()
        val hourlyRows = hourlyRepository.findInBucketRange(from, to)
        if (hourlyRows.isEmpty()) return
        hourlyRows.groupBy { it.subscriptionId }.forEach { (subscriptionId, group) ->
            val daily = dailyRepository.findBySubscriptionIdAndBucketStart(subscriptionId, day)
                ?: SubscriptionTrafficDaily(subscriptionId = subscriptionId, bucketStart = day)
            daily.rxBytesTotal = group.sumOf { it.rxBytesTotal }
            daily.txBytesTotal = group.sumOf { it.txBytesTotal }
            daily.maxMbpsDown = group.maxOfOrNull { it.maxMbpsDown } ?: 0.0
            daily.maxMbpsUp = group.maxOfOrNull { it.maxMbpsUp } ?: 0.0
            daily.p95MbpsDown = percentile(group.map { it.p95MbpsDown }, 0.95)
            daily.p95MbpsUp = percentile(group.map { it.p95MbpsUp }, 0.95)
            daily.activeHours = group.count { it.rxBytesTotal > 0 || it.txBytesTotal > 0 }
            dailyRepository.save(daily)
        }
    }

    @Transactional
    open fun rollupMonthly(yearMonth: YearMonth) {
        val from = yearMonth.atDay(1)
        val to = yearMonth.plusMonths(1).atDay(1)
        val dailyRows = dailyRepository.findInBucketRange(from, to)
        if (dailyRows.isEmpty()) return
        val label = yearMonth.toString()
        dailyRows.groupBy { it.subscriptionId }.forEach { (subscriptionId, group) ->
            val monthly = monthlyRepository.findBySubscriptionIdAndYearMonth(subscriptionId, label)
                ?: SubscriptionTrafficMonthly(subscriptionId = subscriptionId, yearMonth = label)
            monthly.rxBytesTotal = group.sumOf { it.rxBytesTotal }
            monthly.txBytesTotal = group.sumOf { it.txBytesTotal }
            monthly.maxMbpsDown = group.maxOfOrNull { it.maxMbpsDown } ?: 0.0
            monthly.maxMbpsUp = group.maxOfOrNull { it.maxMbpsUp } ?: 0.0
            monthly.p95MbpsDown = percentile(group.map { it.p95MbpsDown }, 0.95)
            monthly.p95MbpsUp = percentile(group.map { it.p95MbpsUp }, 0.95)
            monthly.activeDays = group.count { it.rxBytesTotal > 0 || it.txBytesTotal > 0 }
            monthlyRepository.save(monthly)
        }
    }

    private fun truncateHour(value: LocalDateTime): LocalDateTime =
        value.truncatedTo(ChronoUnit.HOURS)

    private fun percentile(values: List<Double>, p: Double): Double {
        if (values.isEmpty()) return 0.0
        val sorted = values.sorted()
        val index = ceil(p * sorted.size).toInt().coerceIn(1, sorted.size) - 1
        return sorted[index]
    }
}

@Service
open class SubscriptionTrafficRetentionService(
    private val sampleRepository: SubscriptionTrafficSampleRepository,
    private val hourlyRepository: SubscriptionTrafficHourlyRepository,
    private val dailyRepository: SubscriptionTrafficDailyRepository,
    private val networkHourRepository: NetworkTrafficHourOfDayRepository,
    private val trafficProperties: TrafficProperties
) {
    companion object {
        private val logger = LoggerFactory.getLogger(SubscriptionTrafficRetentionService::class.java)
    }

    @Transactional
    open fun purgeExpired(): PurgeResult {
        val now = LocalDateTime.now()
        val rawDeleted = sampleRepository.deleteOlderThan(now.minusDays(trafficProperties.retention.rawDays.toLong()))
        val hourlyDeleted = hourlyRepository.deleteOlderThan(now.minusDays(trafficProperties.retention.hourlyDays.toLong()))
        val dailyDeleted = dailyRepository.deleteOlderThan(now.toLocalDate().minusDays(trafficProperties.retention.dailyDays.toLong()))
        val networkHourDeleted = networkHourRepository.deleteOlderThan(
            now.toLocalDate().minusDays(trafficProperties.retention.networkHourDays.toLong())
        )
        logger.info(
            "Traffic retention raw={} hourly={} daily={} networkHour={}",
            rawDeleted,
            hourlyDeleted,
            dailyDeleted,
            networkHourDeleted
        )
        return PurgeResult(rawDeleted, hourlyDeleted, dailyDeleted, networkHourDeleted)
    }

    data class PurgeResult(
        val rawDeleted: Int,
        val hourlyDeleted: Int,
        val dailyDeleted: Int,
        val networkHourDeleted: Int
    )
}
