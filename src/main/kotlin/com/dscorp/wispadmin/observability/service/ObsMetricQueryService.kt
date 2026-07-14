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
        val dbTimeByRoute = spanRepository.aggregateDbTimeByRoute(fromMs, toMs, release?.takeIf { it.isNotBlank() }).associate {
            (it[0]?.toString() ?: "") to (it[1] as Number).toLong()
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
}
