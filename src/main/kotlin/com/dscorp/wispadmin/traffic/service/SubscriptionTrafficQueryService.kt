package com.dscorp.wispadmin.traffic.service

import com.dscorp.wispadmin.traffic.dto.SubscriptionTrafficDayDto
import com.dscorp.wispadmin.traffic.dto.SubscriptionTrafficDayTotalsDto
import com.dscorp.wispadmin.traffic.dto.SubscriptionTrafficLatestDto
import com.dscorp.wispadmin.traffic.dto.SubscriptionTrafficPointDto
import com.dscorp.wispadmin.traffic.dto.SubscriptionTrafficSeriesDto
import com.dscorp.wispadmin.traffic.dto.SubscriptionTrafficSummaryDto
import com.dscorp.wispadmin.traffic.entity.SubscriptionTrafficSample
import com.dscorp.wispadmin.traffic.entity.TrafficSampleStatus
import com.dscorp.wispadmin.traffic.repository.SubscriptionTrafficDailyRepository
import com.dscorp.wispadmin.traffic.repository.SubscriptionTrafficHourlyRepository
import com.dscorp.wispadmin.traffic.repository.SubscriptionTrafficMonthlyRepository
import com.dscorp.wispadmin.traffic.repository.SubscriptionTrafficSampleRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter

@Service
open class SubscriptionTrafficQueryService(
    private val sampleRepository: SubscriptionTrafficSampleRepository,
    private val hourlyRepository: SubscriptionTrafficHourlyRepository,
    private val dailyRepository: SubscriptionTrafficDailyRepository,
    private val monthlyRepository: SubscriptionTrafficMonthlyRepository
) {
    companion object {
        private const val MAX_POINTS = 500
    }

    @Transactional(readOnly = true)
    open fun getSeries(
        subscriptionId: Int,
        granularity: String,
        from: LocalDateTime?,
        to: LocalDateTime?
    ): SubscriptionTrafficSeriesDto {
        val normalized = granularity.lowercase()
        val rangeTo = to ?: LocalDateTime.now()
        val rangeFrom = from ?: defaultFrom(normalized, rangeTo)
        val points = when (normalized) {
            "sample" -> sampleRepository
                .findBySubscriptionIdAndBucketStartBetweenOrderByBucketStartAsc(subscriptionId, rangeFrom, rangeTo)
                .filter { it.sampleStatus == TrafficSampleStatus.OK && it.rxBytesDelta != null && it.txBytesDelta != null }
                .takeLast(MAX_POINTS)
                .map {
                    SubscriptionTrafficPointDto(
                        bucketStart = it.bucketStart.toString(),
                        rxBytes = it.rxBytesDelta ?: 0,
                        txBytes = it.txBytesDelta ?: 0,
                        avgMbpsDown = it.avgMbpsDown ?: 0.0,
                        avgMbpsUp = it.avgMbpsUp ?: 0.0
                    )
                }
            "hourly" -> hourlyRepository
                .findBySubscriptionIdAndBucketStartBetweenOrderByBucketStartAsc(subscriptionId, rangeFrom, rangeTo)
                .takeLast(MAX_POINTS)
                .map {
                    SubscriptionTrafficPointDto(
                        bucketStart = it.bucketStart.toString(),
                        rxBytes = it.rxBytesTotal,
                        txBytes = it.txBytesTotal,
                        avgMbpsDown = it.maxMbpsDown,
                        avgMbpsUp = it.maxMbpsUp
                    )
                }
            "daily" -> dailyRepository
                .findBySubscriptionIdAndBucketStartBetweenOrderByBucketStartAsc(
                    subscriptionId,
                    rangeFrom.toLocalDate(),
                    rangeTo.toLocalDate()
                )
                .takeLast(MAX_POINTS)
                .map {
                    SubscriptionTrafficPointDto(
                        bucketStart = it.bucketStart.toString(),
                        rxBytes = it.rxBytesTotal,
                        txBytes = it.txBytesTotal,
                        avgMbpsDown = it.maxMbpsDown,
                        avgMbpsUp = it.maxMbpsUp
                    )
                }
            else -> emptyList()
        }
        return SubscriptionTrafficSeriesDto(
            subscriptionId = subscriptionId,
            granularity = normalized,
            points = points
        )
    }

    @Transactional(readOnly = true)
    open fun getSeriesByIp(
        clientIp: String,
        granularity: String,
        from: LocalDateTime?,
        to: LocalDateTime?
    ): SubscriptionTrafficSeriesDto {
        val normalized = granularity.lowercase()
        val rangeTo = to ?: LocalDateTime.now()
        val rangeFrom = from ?: defaultFrom(normalized, rangeTo)
        val points = when (normalized) {
            "sample" -> sampleRepository
                .findByClientIpAndBucketStartBetweenOrderByBucketStartAsc(clientIp, rangeFrom, rangeTo)
                .filter { it.sampleStatus == TrafficSampleStatus.OK && it.rxBytesDelta != null && it.txBytesDelta != null }
                .takeLast(MAX_POINTS)
                .map {
                    SubscriptionTrafficPointDto(
                        bucketStart = it.bucketStart.toString(),
                        rxBytes = it.rxBytesDelta ?: 0,
                        txBytes = it.txBytesDelta ?: 0,
                        avgMbpsDown = it.avgMbpsDown ?: 0.0,
                        avgMbpsUp = it.avgMbpsUp ?: 0.0
                    )
                }
            else -> emptyList()
        }
        val labeled = sampleRepository.findTopByClientIpOrderByBucketStartDesc(clientIp)?.subscriptionId ?: 0
        return SubscriptionTrafficSeriesDto(
            subscriptionId = labeled,
            granularity = normalized,
            points = points,
            clientIp = clientIp,
        )
    }

    @Transactional(readOnly = true)
    open fun getLatest(subscriptionId: Int): SubscriptionTrafficLatestDto {
        val latest = sampleRepository.findTopBySubscriptionIdOrderByBucketStartDesc(subscriptionId)
        return toLatestDto(subscriptionId, latest)
    }

    @Transactional(readOnly = true)
    open fun getLatestByIp(clientIp: String): SubscriptionTrafficLatestDto {
        val latest = sampleRepository.findTopByClientIpOrderByBucketStartDesc(clientIp)
        return toLatestDto(latest?.subscriptionId ?: 0, latest).copy(
            clientIp = clientIp,
            ip = latest?.clientIp ?: clientIp,
        )
    }

    @Transactional(readOnly = true)
    open fun getSummary(subscriptionId: Int, month: String?): SubscriptionTrafficSummaryDto {
        val yearMonth = month?.let { YearMonth.parse(it) } ?: YearMonth.now()
        val label = yearMonth.format(DateTimeFormatter.ofPattern("yyyy-MM"))
        val monthly = monthlyRepository.findBySubscriptionIdAndYearMonth(subscriptionId, label)
        if (monthly != null) {
            return SubscriptionTrafficSummaryDto(
                subscriptionId = subscriptionId,
                yearMonth = label,
                rxBytesTotal = monthly.rxBytesTotal,
                txBytesTotal = monthly.txBytesTotal,
                rxGbTotal = bytesToGb(monthly.rxBytesTotal),
                txGbTotal = bytesToGb(monthly.txBytesTotal),
                maxMbpsDown = monthly.maxMbpsDown,
                maxMbpsUp = monthly.maxMbpsUp,
                p95MbpsDown = monthly.p95MbpsDown,
                p95MbpsUp = monthly.p95MbpsUp,
                activeDays = monthly.activeDays
            )
        }
        val from = yearMonth.atDay(1)
        val to = yearMonth.plusMonths(1).atDay(1)
        val dailyRows = dailyRepository.findBySubscriptionIdAndBucketStartBetweenOrderByBucketStartAsc(
            subscriptionId,
            from,
            to.minusDays(1)
        )
        if (dailyRows.isEmpty()) {
            return SubscriptionTrafficSummaryDto(
                subscriptionId = subscriptionId,
                yearMonth = label,
                rxBytesTotal = 0,
                txBytesTotal = 0,
                rxGbTotal = 0.0,
                txGbTotal = 0.0,
                maxMbpsDown = 0.0,
                maxMbpsUp = 0.0,
                p95MbpsDown = 0.0,
                p95MbpsUp = 0.0,
                activeDays = 0,
            )
        }
        val rxTotal = dailyRows.sumOf { it.rxBytesTotal }
        val txTotal = dailyRows.sumOf { it.txBytesTotal }
        return SubscriptionTrafficSummaryDto(
            subscriptionId = subscriptionId,
            yearMonth = label,
            rxBytesTotal = rxTotal,
            txBytesTotal = txTotal,
            rxGbTotal = bytesToGb(rxTotal),
            txGbTotal = bytesToGb(txTotal),
            maxMbpsDown = dailyRows.maxOfOrNull { it.maxMbpsDown } ?: 0.0,
            maxMbpsUp = dailyRows.maxOfOrNull { it.maxMbpsUp } ?: 0.0,
            p95MbpsDown = dailyRows.maxOfOrNull { it.p95MbpsDown } ?: 0.0,
            p95MbpsUp = dailyRows.maxOfOrNull { it.p95MbpsUp } ?: 0.0,
            activeDays = dailyRows.count { it.rxBytesTotal > 0 || it.txBytesTotal > 0 }
        )
    }

    @Transactional(readOnly = true)
    open fun getToday(subscriptionId: Int): SubscriptionTrafficDayDto {
        return buildDayView(subscriptionId, LocalDate.now())
    }

    @Transactional(readOnly = true)
    open fun getDay(subscriptionId: Int, date: LocalDate): SubscriptionTrafficDayDto {
        return buildDayView(subscriptionId, date)
    }

    private fun buildDayView(subscriptionId: Int, date: LocalDate): SubscriptionTrafficDayDto {
        val from = date.atStartOfDay()
        val to = date.plusDays(1).atStartOfDay()
        val samples = sampleRepository.findBySubscriptionIdAndBucketStartBetweenOrderByBucketStartAsc(
            subscriptionId,
            from,
            to.minusSeconds(1)
        ).filter { it.sampleStatus == TrafficSampleStatus.OK && it.rxBytesDelta != null && it.txBytesDelta != null }
        val hourlyPoints = aggregateSamplesToHourlyPoints(samples)
        val totals = SubscriptionTrafficDayTotalsDto(
            rxBytes = hourlyPoints.sumOf { it.rxBytes },
            txBytes = hourlyPoints.sumOf { it.txBytes }
        )
        val peak = hourlyPoints.maxByOrNull { it.rxBytes + it.txBytes }
        val peakHour = peak?.let { parseHourFromBucket(it.bucketStart) }
        return SubscriptionTrafficDayDto(
            subscriptionId = subscriptionId,
            date = date.toString(),
            points = hourlyPoints,
            totals = totals,
            peakHour = peakHour,
            peakHourLabel = peakHour?.let { formatHourLabel(it) }
        )
    }

    private fun aggregateSamplesToHourlyPoints(samples: List<SubscriptionTrafficSample>): List<SubscriptionTrafficPointDto> {
        if (samples.isEmpty()) return emptyList()
        return samples
            .groupBy { it.bucketStart.hour }
            .toSortedMap()
            .map { (_, group) ->
                val bucketStart = group.first().bucketStart.withMinute(0).withSecond(0).withNano(0)
                SubscriptionTrafficPointDto(
                    bucketStart = bucketStart.toString(),
                    rxBytes = group.sumOf { it.rxBytesDelta ?: 0 },
                    txBytes = group.sumOf { it.txBytesDelta ?: 0 },
                    avgMbpsDown = group.mapNotNull { it.avgMbpsDown }.averageOrZero(),
                    avgMbpsUp = group.mapNotNull { it.avgMbpsUp }.averageOrZero()
                )
            }
    }

    private fun parseHourFromBucket(bucketStart: String): Int? =
        bucketStart.substringAfter("T", missingDelimiterValue = "").substringBefore(":").toIntOrNull()

    private fun formatHourLabel(hour: Int): String {
        val suffix = if (hour < 12) "a.m." else "p.m."
        val display = when {
            hour == 0 -> 12
            hour > 12 -> hour - 12
            else -> hour
        }
        return "$display:00 $suffix"
    }

    private fun toLatestDto(subscriptionId: Int, latest: SubscriptionTrafficSample?): SubscriptionTrafficLatestDto {
        val ip = latest?.clientIp?.takeIf { it.isNotBlank() }
        return SubscriptionTrafficLatestDto(
            subscriptionId = subscriptionId,
            bucketStart = latest?.bucketStart?.toString(),
            rxBytes = latest?.rxBytesDelta,
            txBytes = latest?.txBytesDelta,
            avgMbpsDown = latest?.avgMbpsDown,
            avgMbpsUp = latest?.avgMbpsUp,
            polledAt = latest?.bucketStart?.toString(),
            ip = ip,
            clientIp = ip,
            sampleStatus = latest?.sampleStatus?.name,
            hostDeviceId = latest?.hostDeviceId,
            collectedAt = latest?.collectedAt?.toString(),
            queueId = latest?.queueId,
            id = latest?.id,
        )
    }

    private fun defaultFrom(granularity: String, to: LocalDateTime): LocalDateTime = when (granularity) {
        "daily" -> to.minusDays(90)
        "hourly" -> to.minusDays(7)
        else -> to.minusDays(7)
    }

    private fun bytesToGb(bytes: Long): Double = bytes / (1024.0 * 1024.0 * 1024.0)

    private fun List<Double>.averageOrZero(): Double = if (isEmpty()) 0.0 else average()
}
