package com.dscorp.wispadmin.observability.service

import com.dscorp.wispadmin.observability.dto.EndpointMetricAggregateDto
import com.dscorp.wispadmin.observability.dto.EndpointMetricPointDto
import com.dscorp.wispadmin.observability.repository.ObsEndpointMetricRepository
import com.dscorp.wispadmin.observability.repository.ObsSpanRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.time.ZoneId

@Service
class ObsMetricQueryService(
    private val metricRepository: ObsEndpointMetricRepository,
    private val spanRepository: ObsSpanRepository,
    @Value("\${app.timezone:America/Lima}") private val appTimezone: String
) {

    fun timeSeries(from: LocalDateTime, to: LocalDateTime, route: String?): List<EndpointMetricPointDto> {
        return metricRepository.findByBucketStartBetweenOrderByBucketStartAsc(from, to)
            .filter { route.isNullOrBlank() || it.route == route }
            .map {
                EndpointMetricPointDto(
                    bucketStart = it.bucketStart,
                    httpMethod = it.httpMethod,
                    route = it.route,
                    sampleCount = it.sampleCount,
                    errorCount = it.errorCount,
                    p50Ms = it.p50Ms,
                    p95Ms = it.p95Ms,
                    p99Ms = it.p99Ms,
                    avgMs = it.avgMs,
                    maxMs = it.maxMs,
                    throughputPerMin = it.throughputPerMin
                )
            }
    }

    fun aggregate(from: LocalDateTime, to: LocalDateTime, release: String? = null): List<EndpointMetricAggregateDto> {
        val zone = ZoneId.of(appTimezone)
        val fromMs = from.atZone(zone).toInstant().toEpochMilli()
        val toMs = to.atZone(zone).toInstant().toEpochMilli()
        val normalizedRelease = release?.takeIf { it.isNotBlank() }
        val dbTimeByRoute = spanRepository.aggregateDbTimeByRoute(fromMs, toMs, normalizedRelease).associate {
            (it[0]?.toString() ?: "") to (it[1] as Number).toLong()
        }
        if (normalizedRelease != null) {
            return aggregateFromSpans(fromMs, toMs, normalizedRelease, dbTimeByRoute)
        }
        return metricRepository.aggregateByRoute(from, to).map {
            val totalRequests = (it[2] as Number).toLong()
            val totalErrors = (it[3] as Number).toLong()
            val avgMs = (it[4] as Number).toDouble()
            val route = it[0]?.toString()
            val dbTimeMs = route?.let { r -> dbTimeByRoute[r] }
            val totalRouteMs = avgMs * totalRequests
            val dbTimeRatio = if (dbTimeMs != null && totalRouteMs > 0) {
                (dbTimeMs / totalRouteMs).coerceIn(0.0, 1.0)
            } else null
            EndpointMetricAggregateDto(
                route = route,
                httpMethod = it[1]?.toString(),
                totalRequests = totalRequests,
                totalErrors = totalErrors,
                errorRate = if (totalRequests > 0) totalErrors.toDouble() / totalRequests else 0.0,
                avgMs = avgMs,
                p95Ms = (it[5] as Number).toLong(),
                p99Ms = (it[6] as Number).toLong(),
                maxMs = (it[7] as Number).toLong(),
                dbTimeMs = dbTimeMs,
                dbTimeRatio = dbTimeRatio
            )
        }
    }

    private fun aggregateFromSpans(
        fromMs: Long,
        toMs: Long,
        release: String,
        dbTimeByRoute: Map<String, Long>
    ): List<EndpointMetricAggregateDto> {
        val rows = spanRepository.rootSpanMetricsByRoute(fromMs, toMs, release)
        return rows
            .groupBy { Pair(it[0]?.toString(), it.getOrNull(1)?.toString()) }
            .map { (key, group) ->
                val route = key.first
                val durations = group.mapNotNull { (it[2] as? Number)?.toLong() }.sorted()
                val totalRequests = group.size.toLong()
                val totalErrors = group.count { it.getOrNull(3)?.toString() == "ERROR" }.toLong()
                val avgMs = if (durations.isNotEmpty()) durations.average() else 0.0
                val dbTimeMs = route?.let { dbTimeByRoute[it] }
                val totalRouteMs = avgMs * totalRequests
                val dbTimeRatio = if (dbTimeMs != null && totalRouteMs > 0) {
                    (dbTimeMs / totalRouteMs).coerceIn(0.0, 1.0)
                } else null
                EndpointMetricAggregateDto(
                    route = route,
                    httpMethod = key.second,
                    totalRequests = totalRequests,
                    totalErrors = totalErrors,
                    errorRate = if (totalRequests > 0) totalErrors.toDouble() / totalRequests else 0.0,
                    avgMs = avgMs,
                    p95Ms = percentile(durations, 0.95),
                    p99Ms = percentile(durations, 0.99),
                    maxMs = durations.lastOrNull() ?: 0L,
                    dbTimeMs = dbTimeMs,
                    dbTimeRatio = dbTimeRatio
                )
            }
            .sortedByDescending { it.p95Ms }
    }

    private fun percentile(sortedAsc: List<Long>, p: Double): Long {
        if (sortedAsc.isEmpty()) return 0L
        val index = (Math.ceil(p * sortedAsc.size).toInt()).coerceIn(1, sortedAsc.size) - 1
        return sortedAsc[index]
    }
}
