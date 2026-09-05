package com.dscorp.wispadmin.traffic.service

import com.dscorp.wispadmin.traffic.config.TrafficProperties
import com.dscorp.wispadmin.traffic.entity.SubscriptionTrafficDaily
import com.dscorp.wispadmin.traffic.entity.SubscriptionTrafficFiveMinute
import com.dscorp.wispadmin.traffic.entity.SubscriptionTrafficHourly
import com.dscorp.wispadmin.traffic.entity.SubscriptionTrafficMonthly
import com.dscorp.wispadmin.traffic.entity.SubscriptionTrafficSample
import com.dscorp.wispadmin.traffic.entity.TrafficSampleStatus
import com.dscorp.wispadmin.traffic.repository.SubscriptionTrafficDailyRepository
import com.dscorp.wispadmin.traffic.repository.SubscriptionTrafficFiveMinuteRepository
import com.dscorp.wispadmin.traffic.repository.SubscriptionTrafficHourlyRepository
import com.dscorp.wispadmin.traffic.repository.SubscriptionTrafficMonthlyRepository
import com.dscorp.wispadmin.traffic.repository.SubscriptionTrafficSampleRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit
import kotlin.math.ceil

@Service
open class SubscriptionTrafficRollupService(
    private val sampleRepository: SubscriptionTrafficSampleRepository,
    private val fiveMinuteRepository: SubscriptionTrafficFiveMinuteRepository,
    private val hourlyRepository: SubscriptionTrafficHourlyRepository,
    private val dailyRepository: SubscriptionTrafficDailyRepository,
    private val monthlyRepository: SubscriptionTrafficMonthlyRepository,
    private val trafficProperties: TrafficProperties
) {
    companion object {
        private val logger = LoggerFactory.getLogger(SubscriptionTrafficRollupService::class.java)
    }

    @Transactional
    open fun rollupFiveMinute(from: LocalDateTime, to: LocalDateTime, oneMinuteSince: LocalDateTime? = null) {
        val observations = sampleRepository.findAllInBucketRange(from, to)
        if (observations.isEmpty()) return
        val cutover = oneMinuteSince ?: parseOneMinuteSince()
        val existing = fiveMinuteRepository.findInBucketRange(from, to)
            .associateBy { it.subscriptionId to it.bucketStart }
            .toMutableMap()
        val toSave = mutableListOf<SubscriptionTrafficFiveMinute>()
        observations.filter { it.subscriptionId != null }.groupBy { it.subscriptionId!! to truncateFiveMinutes(it.bucketStart) }.forEach { (key, group) ->
            val (subscriptionId, bucketStart) = key
            if (bucketStart < from || bucketStart >= to) return@forEach
            val valid = group.filter { it.sampleStatus == TrafficSampleStatus.OK && it.rxBytesDelta != null && it.txBytesDelta != null }
            val row = existing[key]
                ?: SubscriptionTrafficFiveMinute(subscriptionId = subscriptionId, hostDeviceId = group.first().hostDeviceId, bucketStart = bucketStart)
            val expected = expectedFiveMinuteSamples(bucketStart, cutover)
            applyMetrics(
                row,
                valid,
                expected,
                group.firstOrNull()?.planDownloadMbps,
                group.firstOrNull()?.planUploadMbps
            )
            existing[key] = row
            toSave.add(row)
        }
        if (toSave.isNotEmpty()) fiveMinuteRepository.saveAll(toSave)
    }

    @Transactional
    open fun rollupHourly(from: LocalDateTime, to: LocalDateTime) {
        val rows = fiveMinuteRepository.findInBucketRange(from, to)
        if (rows.isEmpty()) return
        val existing = hourlyRepository.findInBucketRange(from, to)
            .associateBy { it.subscriptionId to it.bucketStart }
            .toMutableMap()
        val toSave = mutableListOf<SubscriptionTrafficHourly>()
        rows.groupBy { it.subscriptionId to truncateHour(it.bucketStart) }.forEach { (key, group) ->
            val (subscriptionId, bucketStart) = key
            if (bucketStart < from || bucketStart >= to) return@forEach
            val hourly = existing[key]
                ?: SubscriptionTrafficHourly(subscriptionId = subscriptionId, bucketStart = bucketStart)
            hourly.rxBytesTotal = group.sumOf { it.rxBytesTotal }
            hourly.txBytesTotal = group.sumOf { it.txBytesTotal }
            hourly.maxMbpsDown = group.maxOfOrNull { it.maxMbpsDown } ?: 0.0
            hourly.maxMbpsUp = group.maxOfOrNull { it.maxMbpsUp } ?: 0.0
            hourly.p95MbpsDown = percentile(group.map { it.avgMbpsDown }, 0.95)
            hourly.p95MbpsUp = percentile(group.map { it.avgMbpsUp }, 0.95)
            hourly.avgMbpsDown = group.map { it.avgMbpsDown }.averageOrZero()
            hourly.avgMbpsUp = group.map { it.avgMbpsUp }.averageOrZero()
            hourly.sampleCount = group.sumOf { it.sampleCount }
            hourly.expectedSampleCount = 12
            hourly.coveragePct = coverage(group.size, 12)
            hourly.planDownloadMbps = group.lastOrNull()?.planDownloadMbps
            hourly.planUploadMbps = group.lastOrNull()?.planUploadMbps
            hourly.utilizationDownPct = utilization(hourly.p95MbpsDown, hourly.planDownloadMbps)
            hourly.utilizationUpPct = utilization(hourly.p95MbpsUp, hourly.planUploadMbps)
            hourly.secondsOver80 = group.sumOf { it.secondsOver80 }
            hourly.secondsOver90 = group.sumOf { it.secondsOver90 }
            hourly.secondsOver95 = group.sumOf { it.secondsOver95 }
            existing[key] = hourly
            toSave.add(hourly)
        }
        if (toSave.isNotEmpty()) hourlyRepository.saveAll(toSave)
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
            daily.p95MbpsDown = percentile(group.map { it.avgMbpsDown }, 0.95)
            daily.p95MbpsUp = percentile(group.map { it.avgMbpsUp }, 0.95)
            daily.avgMbpsDown = group.map { it.avgMbpsDown }.averageOrZero()
            daily.avgMbpsUp = group.map { it.avgMbpsUp }.averageOrZero()
            daily.sampleCount = group.sumOf { it.sampleCount }
            daily.expectedSampleCount = group.sumOf { it.expectedSampleCount }
            daily.coveragePct = coverage(group.size, 24)
            daily.planDownloadMbps = group.lastOrNull()?.planDownloadMbps
            daily.planUploadMbps = group.lastOrNull()?.planUploadMbps
            daily.utilizationDownPct = utilization(daily.p95MbpsDown, daily.planDownloadMbps)
            daily.utilizationUpPct = utilization(daily.p95MbpsUp, daily.planUploadMbps)
            daily.secondsOver80 = group.sumOf { it.secondsOver80 }
            daily.secondsOver90 = group.sumOf { it.secondsOver90 }
            daily.secondsOver95 = group.sumOf { it.secondsOver95 }
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
            monthly.p95MbpsDown = percentile(group.map { it.avgMbpsDown }, 0.95)
            monthly.p95MbpsUp = percentile(group.map { it.avgMbpsUp }, 0.95)
            monthly.avgMbpsDown = group.map { it.avgMbpsDown }.averageOrZero()
            monthly.avgMbpsUp = group.map { it.avgMbpsUp }.averageOrZero()
            monthly.sampleCount = group.sumOf { it.sampleCount }
            monthly.expectedSampleCount = group.sumOf { it.expectedSampleCount }
            monthly.coveragePct = coverage(monthly.sampleCount, monthly.expectedSampleCount)
            monthly.planDownloadMbps = group.lastOrNull()?.planDownloadMbps
            monthly.planUploadMbps = group.lastOrNull()?.planUploadMbps
            monthly.utilizationDownPct = utilization(monthly.p95MbpsDown, monthly.planDownloadMbps)
            monthly.utilizationUpPct = utilization(monthly.p95MbpsUp, monthly.planUploadMbps)
            monthly.activeDays = group.count { it.rxBytesTotal > 0 || it.txBytesTotal > 0 }
            monthlyRepository.save(monthly)
        }
        logger.info("Traffic monthly rollup completed month={}", label)
    }

    fun expectedFiveMinuteSamples(bucketStart: LocalDateTime, oneMinuteSince: LocalDateTime?): Int {
        if (oneMinuteSince != null && bucketStart.isBefore(oneMinuteSince)) return 1
        return (5 / trafficProperties.poll.bucketMinutes.coerceAtLeast(1)).coerceAtLeast(1)
    }

    private fun parseOneMinuteSince(): LocalDateTime? {
        val raw = trafficProperties.aggregation.oneMinuteSince?.trim().orEmpty()
        if (raw.isEmpty()) return null
        return try {
            LocalDateTime.parse(raw)
        } catch (_: DateTimeParseException) {
            null
        }
    }

    private fun truncateHour(value: LocalDateTime): LocalDateTime =
        value.truncatedTo(ChronoUnit.HOURS)

    private fun truncateFiveMinutes(value: LocalDateTime): LocalDateTime =
        value.withMinute((value.minute / 5) * 5).withSecond(0).withNano(0)

    private fun percentile(values: List<Double>, p: Double): Double {
        if (values.isEmpty()) return 0.0
        val sorted = values.sorted()
        val index = ceil(p * sorted.size).toInt().coerceIn(1, sorted.size) - 1
        return sorted[index]
    }

    private fun List<Double>.averageOrZero() = if (isEmpty()) 0.0 else average()
    private fun coverage(actual: Int, expected: Int) = if (expected <= 0) 0.0 else (actual * 100.0 / expected).coerceIn(0.0, 100.0)
    private fun utilization(value: Double, plan: Int?) = plan?.takeIf { it > 0 }?.let { value * 100.0 / it }

    private fun applyMetrics(
        row: SubscriptionTrafficFiveMinute,
        valid: List<SubscriptionTrafficSample>,
        expected: Int,
        planDown: Int?,
        planUp: Int?
    ) {
        val down = valid.mapNotNull { it.avgMbpsDown }
        val up = valid.mapNotNull { it.avgMbpsUp }
        row.rxBytesTotal = valid.sumOf { it.rxBytesDelta ?: 0 }
        row.txBytesTotal = valid.sumOf { it.txBytesDelta ?: 0 }
        row.avgMbpsDown = down.averageOrZero()
        row.avgMbpsUp = up.averageOrZero()
        row.maxMbpsDown = down.maxOrNull() ?: 0.0
        row.maxMbpsUp = up.maxOrNull() ?: 0.0
        row.p95MbpsDown = percentile(down, .95)
        row.p95MbpsUp = percentile(up, .95)
        row.sampleCount = valid.size
        row.expectedSampleCount = expected
        row.coveragePct = coverage(valid.size, expected)
        row.planDownloadMbps = planDown
        row.planUploadMbps = planUp
        row.utilizationDownPct = utilization(row.p95MbpsDown, planDown)
        row.utilizationUpPct = utilization(row.p95MbpsUp, planUp)
        row.secondsOver80 = thresholdSeconds(valid, 80.0)
        row.secondsOver90 = thresholdSeconds(valid, 90.0)
        row.secondsOver95 = thresholdSeconds(valid, 95.0)
    }

    private fun thresholdSeconds(samples: List<SubscriptionTrafficSample>, threshold: Double): Int =
        samples.sumOf { sample ->
            val plan = sample.planDownloadMbps?.takeIf { it > 0 } ?: return@sumOf 0
            if ((sample.avgMbpsDown ?: 0.0) * 100.0 / plan >= threshold) sample.intervalSeconds ?: 0 else 0
        }
}
