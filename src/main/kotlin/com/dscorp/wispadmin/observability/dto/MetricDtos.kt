package com.dscorp.wispadmin.observability.dto

import java.time.LocalDateTime

data class EndpointMetricPointDto(
    val bucketStart: LocalDateTime?,
    val httpMethod: String?,
    val route: String?,
    val sampleCount: Long,
    val errorCount: Long,
    val p50Ms: Long,
    val p95Ms: Long,
    val p99Ms: Long,
    val avgMs: Double,
    val maxMs: Long,
    val throughputPerMin: Long
)

data class EndpointMetricAggregateDto(
    val route: String?,
    val httpMethod: String?,
    val totalRequests: Long,
    val totalErrors: Long,
    val errorRate: Double,
    val avgMs: Double,
    val p95Ms: Long,
    val p99Ms: Long,
    val maxMs: Long,
    val dbTimeMs: Long? = null,
    val dbTimeRatio: Double? = null
)

data class RumMetricPointDto(
    val bucketStart: LocalDateTime?,
    val page: String?,
    val platform: String?,
    val metricName: String?,
    val release: String? = null,
    val sampleCount: Long,
    val p50: Double,
    val p75: Double,
    val p95: Double,
    val p99: Double,
    val min: Double,
    val max: Double,
    val avg: Double,
    val goodCount: Long,
    val needsImprovementCount: Long,
    val poorCount: Long
)

data class RumMetricAggregateDto(
    val page: String?,
    val metricName: String?,
    val release: String? = null,
    val sampleCount: Long,
    val goodCount: Long,
    val needsImprovementCount: Long,
    val poorCount: Long,
    val goodRate: Double,
    val needsImprovementRate: Double,
    val poorRate: Double,
    val avg: Double,
    val p75: Double,
    val p95: Double,
    val p99: Double,
    val max: Double,
    val rating: String
)

data class EndpointDetailMetricsDto(
    val route: String?,
    val httpMethod: String?,
    val totalRequests: Long,
    val totalErrors: Long,
    val errorRate: Double,
    val avgMs: Double,
    val p50Ms: Long,
    val p95Ms: Long,
    val p99Ms: Long,
    val maxMs: Long,
    val dbTimeMs: Long? = null,
    val dbTimeRatio: Double? = null
)

data class EndpointDetailDto(
    val route: String?,
    val release: String?,
    val from: LocalDateTime,
    val to: LocalDateTime,
    val metrics: EndpointDetailMetricsDto,
    val timeSeries: List<EndpointMetricPointDto>,
    val topQueries: List<DbQueryAggregateDto>,
    val nPlusOne: List<NPlusOneCandidateDto>,
    val issues: List<IssueSummaryDto>,
    val sampleTraces: List<TraceSummaryDto>
)

data class SystemMetricPointDto(
    val bucketStart: LocalDateTime?,
    val sampledAt: LocalDateTime?,
    val heapUsedBytes: Long,
    val heapMaxBytes: Long,
    val nonHeapUsedBytes: Long,
    val cpuProcess: Double,
    val cpuSystem: Double,
    val osMemFreeBytes: Long,
    val osMemTotalBytes: Long,
    val threadsLive: Int,
    val threadsDaemon: Int,
    val threadsPeak: Int,
    val gcCount: Long,
    val gcTimeMs: Long,
    val poolActive: Int,
    val poolIdle: Int,
    val poolWaiting: Int,
    val poolTotal: Int,
    val poolMax: Int
)
