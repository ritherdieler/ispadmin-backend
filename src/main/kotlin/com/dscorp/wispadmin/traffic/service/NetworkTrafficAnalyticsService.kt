package com.dscorp.wispadmin.traffic.service

import com.dscorp.wispadmin.traffic.dto.NetworkHourlyProfileDto
import com.dscorp.wispadmin.traffic.dto.NetworkHourlyProfilePointDto
import com.dscorp.wispadmin.traffic.dto.NetworkTrafficInsightsDto
import com.dscorp.wispadmin.traffic.dto.NetworkTrafficTrendDto
import com.dscorp.wispadmin.traffic.dto.NetworkTrafficTrendPointDto
import com.dscorp.wispadmin.traffic.repository.NetworkHourAggregateProjection
import com.dscorp.wispadmin.traffic.repository.NetworkTrafficHourOfDayRepository
import com.dscorp.wispadmin.traffic.repository.SubscriptionTrafficDailyRepository
import com.dscorp.wispadmin.traffic.repository.SubscriptionTrafficHourlyRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.TextStyle
import java.util.Locale

@Service
open class NetworkTrafficAnalyticsService(
    private val networkHourRepository: NetworkTrafficHourOfDayRepository,
    private val hourlyRepository: SubscriptionTrafficHourlyRepository,
    private val dailyRepository: SubscriptionTrafficDailyRepository
) {
    @Transactional(readOnly = true)
    open fun getHourlyProfile(months: Int): NetworkHourlyProfileDto {
        val normalizedMonths = normalizeMonths(months)
        val fromDate = LocalDate.now().minusMonths(normalizedMonths.toLong())
        var rows = networkHourRepository.aggregateHourlyProfile(fromDate)
        if (rows.isEmpty()) {
            rows = hourlyRepository.aggregateNetworkHourlyProfile(fromDate.atStartOfDay())
        }
        val points = fillHourlyProfile(rows)
        val peak = points.maxByOrNull { it.rxBytes + it.txBytes }
        return NetworkHourlyProfileDto(
            months = normalizedMonths,
            points = points,
            peakHour = peak?.hour,
            peakHourLabel = peak?.hourLabel
        )
    }

    @Transactional(readOnly = true)
    open fun getDailyTrend(months: Int): NetworkTrafficTrendDto {
        val normalizedMonths = normalizeMonths(months)
        val fromDate = LocalDate.now().minusMonths(normalizedMonths.toLong())
        val rows = dailyRepository.aggregateNetworkDailyTrend(fromDate)
        val points = rows.map {
            NetworkTrafficTrendPointDto(
                bucketStart = it.getBucketDate().toString(),
                rxBytes = it.getRxBytes(),
                txBytes = it.getTxBytes()
            )
        }
        return NetworkTrafficTrendDto(months = normalizedMonths, points = points)
    }

    @Transactional(readOnly = true)
    open fun getInsights(months: Int): NetworkTrafficInsightsDto {
        val profile = getHourlyProfile(months)
        val trend = getDailyTrend(months)
        val peak = profile.points.maxByOrNull { it.rxBytes + it.txBytes }
        val totalRx = trend.points.sumOf { it.rxBytes }
        val totalTx = trend.points.sumOf { it.txBytes }
        val busiestWeekday = trend.points
            .groupBy { LocalDate.parse(it.bucketStart).dayOfWeek }
            .maxByOrNull { (_, values) -> values.sumOf { point -> point.rxBytes + point.txBytes } }
            ?.key
            ?.getDisplayName(TextStyle.FULL, Locale("es", "PE"))
        val peakHour = peak?.hour
        return NetworkTrafficInsightsDto(
            months = profile.months,
            peakHour = peakHour,
            peakHourLabel = peak?.hourLabel,
            peakHourRange = peakHour?.let { formatHourRange(it) },
            busiestWeekday = busiestWeekday,
            totalRxGb = bytesToGb(totalRx),
            totalTxGb = bytesToGb(totalTx)
        )
    }

    private fun fillHourlyProfile(rows: List<NetworkHourAggregateProjection>): List<NetworkHourlyProfilePointDto> {
        val byHour = rows.associateBy { it.getHourOfDay() }
        return (0..23).map { hour ->
            val row = byHour[hour]
            NetworkHourlyProfilePointDto(
                hour = hour,
                hourLabel = formatHourLabel(hour),
                rxBytes = row?.getRxBytes() ?: 0L,
                txBytes = row?.getTxBytes() ?: 0L
            )
        }
    }

    private fun normalizeMonths(months: Int): Int = when (months) {
        6 -> 6
        12 -> 12
        else -> 3
    }

    private fun formatHourLabel(hour: Int): String {
        val suffix = if (hour < 12) "a.m." else "p.m."
        val display = when {
            hour == 0 -> 12
            hour > 12 -> hour - 12
            else -> hour
        }
        return "$display:00 $suffix"
    }

    private fun formatHourRange(hour: Int): String {
        val endHour = (hour + 1) % 24
        return "Entre ${formatHourLabel(hour)} y ${formatHourLabel(endHour)}"
    }

    private fun bytesToGb(bytes: Long): Double = bytes / (1024.0 * 1024.0 * 1024.0)
}
