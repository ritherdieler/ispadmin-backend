package com.dscorp.wispadmin.traffic.dto

import java.time.LocalDateTime

data class SubscriptionTrafficPointDto(
    val bucketStart: String,
    val rxBytes: Long,
    val txBytes: Long,
    val avgMbpsDown: Double,
    val avgMbpsUp: Double
)

data class SubscriptionTrafficSeriesDto(
    val subscriptionId: Int,
    val granularity: String,
    val points: List<SubscriptionTrafficPointDto>,
    val clientIp: String? = null,
)

data class SubscriptionTrafficLatestDto(
    val subscriptionId: Int,
    val bucketStart: String?,
    val rxBytes: Long?,
    val txBytes: Long?,
    val avgMbpsDown: Double?,
    val avgMbpsUp: Double?,
    val polledAt: String?,
    val ip: String? = null,
    val clientIp: String? = null,
    val sampleStatus: String? = null,
    val hostDeviceId: Int? = null,
    val collectedAt: String? = null,
    val queueId: String? = null,
    val id: Long? = null,
)

data class SubscriptionTrafficSummaryDto(
    val subscriptionId: Int,
    val yearMonth: String,
    val rxBytesTotal: Long,
    val txBytesTotal: Long,
    val rxGbTotal: Double,
    val txGbTotal: Double,
    val maxMbpsDown: Double,
    val maxMbpsUp: Double,
    val p95MbpsDown: Double,
    val p95MbpsUp: Double,
    val activeDays: Int
)

data class SubscriptionTrafficLiveTickDto(
    val subscriptionId: Int,
    val timestamp: String,
    val rxMbps: Double?,
    val txMbps: Double?,
    val rxBytesDelta: Long,
    val txBytesDelta: Long,
    val sessionRxBytes: Long,
    val sessionTxBytes: Long,
    val queueFound: Boolean = true
)

data class SubscriptionTrafficPollResultDto(
    val devicesPolled: Int = 0,
    val subscriptionsMatched: Int = 0,
    val samplesWritten: Int = 0,
    val skippedReason: String? = null,
    val error: String? = null,
    val durationMs: Long = 0
)

data class SubscriptionTrafficDayTotalsDto(
    val rxBytes: Long,
    val txBytes: Long
)

data class SubscriptionTrafficDayDto(
    val subscriptionId: Int,
    val date: String,
    val points: List<SubscriptionTrafficPointDto>,
    val totals: SubscriptionTrafficDayTotalsDto,
    val peakHour: Int?,
    val peakHourLabel: String?
)

data class NetworkHourlyProfilePointDto(
    val hour: Int,
    val hourLabel: String,
    val rxBytes: Long,
    val txBytes: Long
)

data class NetworkHourlyProfileDto(
    val months: Int,
    val points: List<NetworkHourlyProfilePointDto>,
    val peakHour: Int?,
    val peakHourLabel: String?
)

data class NetworkTrafficTrendPointDto(
    val bucketStart: String,
    val rxBytes: Long,
    val txBytes: Long
)

data class NetworkTrafficTrendDto(
    val months: Int,
    val points: List<NetworkTrafficTrendPointDto>
)

data class NetworkTrafficInsightsDto(
    val months: Int,
    val peakHour: Int?,
    val peakHourLabel: String?,
    val peakHourRange: String?,
    val busiestWeekday: String?,
    val totalRxGb: Double,
    val totalTxGb: Double
)
