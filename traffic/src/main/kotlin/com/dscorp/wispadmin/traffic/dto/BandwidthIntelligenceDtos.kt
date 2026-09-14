package com.dscorp.wispadmin.traffic.dto

data class BandwidthRangeDto(val from: String, val to: String, val resolution: String, val coveragePct: Double, val freshness: String, val quality: String)
data class BandwidthPointDto(val bucketStart: String, val rxBytes: Long, val txBytes: Long, val avgMbpsDown: Double, val avgMbpsUp: Double, val p95MbpsDown: Double, val p95MbpsUp: Double, val coveragePct: Double)
data class BandwidthSeriesDto(val meta: BandwidthRangeDto, val points: List<BandwidthPointDto>)
data class BandwidthNetworkDto(val overview: BandwidthOverviewDto, val series: BandwidthSeriesDto)
data class BandwidthOverviewDto(
    val meta: BandwidthRangeDto,
    val totalRxBytes: Long,
    val totalTxBytes: Long,
    val avgMbpsDown: Double,
    val avgMbpsUp: Double,
    val p95MbpsDown: Double,
    val p95MbpsUp: Double,
    val peakMbpsDown: Double,
    val peakMbpsUp: Double,
    val utilizationPct: Double?,
    val growthPct: Double?,
    val activeSubscriptions: Int,
    val openAnomalies: Int
)
data class BandwidthSubscriptionRowDto(
    val subscriptionId: Int,
    val customerName: String,
    val ip: String?,
    val routerId: Int?,
    val routerName: String?,
    val planId: Int?,
    val planName: String?,
    val planDownloadMbps: Int?,
    val planUploadMbps: Int?,
    val rxBytes: Long,
    val txBytes: Long,
    val p95MbpsDown: Double,
    val p95MbpsUp: Double,
    val utilizationPct: Double?,
    val coveragePct: Double,
    val quality: String
)
data class BandwidthSubscriptionPageDto(val meta: BandwidthRangeDto, val items: List<BandwidthSubscriptionRowDto>, val page: Int, val size: Int, val total: Long, val totalPages: Int)
data class BandwidthSubscriptionDetailDto(val subscription: BandwidthSubscriptionRowDto, val series: BandwidthSeriesDto, val anomalies: List<BandwidthAnomalyDto>)
data class BandwidthSourceDto(
    val routerId: Int,
    val routerName: String,
    val status: String,
    val startedAt: String,
    val completedAt: String?,
    val durationMs: Long,
    val lagSeconds: Long,
    val expected: Int,
    val matched: Int,
    val written: Int,
    val missing: Int,
    val invalid: Int,
    val coveragePct: Double,
    val error: String?
)
data class BandwidthAggregationLayerDto(
    val layer: String,
    val consolidatedThrough: String?,
    val lagSeconds: Long
)
data class BandwidthSourcesDto(
    val generatedAt: String,
    val items: List<BandwidthSourceDto>,
    val aggregation: List<BandwidthAggregationLayerDto> = emptyList()
)
data class BandwidthAnomalyDto(
    val id: Long,
    val type: String,
    val status: String,
    val subscriptionId: Int?,
    val routerId: Int?,
    val startedAt: String,
    val endedAt: String?,
    val baseline: Double?,
    val observed: Double?,
    val deviation: Double?,
    val coveragePct: Double,
    val confidence: Double,
    val ruleVersion: String,
    val evidence: String?
)
data class BandwidthAnomalyPageDto(val items: List<BandwidthAnomalyDto>, val page: Int, val size: Int, val total: Long, val totalPages: Int)
