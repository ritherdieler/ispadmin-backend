package com.dscorp.wispadmin.observability.service

import com.dscorp.wispadmin.observability.dto.EndpointDetailDto
import com.dscorp.wispadmin.observability.dto.EndpointDetailMetricsDto
import com.dscorp.wispadmin.observability.dto.TraceSummaryDto
import com.dscorp.wispadmin.observability.dto.toSummaryDto
import com.dscorp.wispadmin.observability.entity.ObsSpan
import com.dscorp.wispadmin.observability.repository.ObsIssueRepository
import com.dscorp.wispadmin.observability.repository.ObsSpanRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.time.ZoneId

@Service
class ObsEndpointDetailService(
    private val metricQueryService: ObsMetricQueryService,
    private val databaseQueryService: ObsDatabaseQueryService,
    private val spanRepository: ObsSpanRepository,
    private val issueRepository: ObsIssueRepository,
    @Value("\${app.timezone:America/Lima}") private val appTimezone: String
) {

    fun detail(route: String, from: LocalDateTime, to: LocalDateTime, release: String?): EndpointDetailDto {
        val zone = ZoneId.of(appTimezone)
        val fromMs = from.atZone(zone).toInstant().toEpochMilli()
        val toMs = to.atZone(zone).toInstant().toEpochMilli()
        val normalizedRelease = release?.takeIf { it.isNotBlank() }

        val metrics = buildMetrics(route, fromMs, toMs, normalizedRelease)
        val timeSeries = metricQueryService.timeSeries(from, to, route)
        val topQueries = databaseQueryService.topQueries(fromMs, toMs, 20, normalizedRelease, route)
        val nPlusOne = databaseQueryService.nPlusOne(fromMs, toMs, 5, normalizedRelease, route)
        val issues = issueRepository.findIssuesByRoute(route, from, to, PageRequest.of(0, 20)).map { it.toSummaryDto() }
        val sampleTraces = buildSampleTraces(route, fromMs, toMs, normalizedRelease)

        return EndpointDetailDto(
            route = route,
            release = normalizedRelease,
            from = from,
            to = to,
            metrics = metrics,
            timeSeries = timeSeries,
            topQueries = topQueries,
            nPlusOne = nPlusOne,
            issues = issues,
            sampleTraces = sampleTraces
        )
    }

    private fun buildMetrics(
        route: String,
        fromMs: Long,
        toMs: Long,
        release: String?
    ): EndpointDetailMetricsDto {
        val rows = spanRepository.rootSpanMetricsByRoute(fromMs, toMs, release)
            .filter { it[0]?.toString() == route }
        val durations = rows.mapNotNull { (it[2] as? Number)?.toLong() }.sorted()
        val totalRequests = rows.size.toLong()
        val totalErrors = rows.count { it.getOrNull(3)?.toString() == "ERROR" }.toLong()
        val avgMs = if (durations.isNotEmpty()) durations.average() else 0.0
        val httpMethod = rows.firstOrNull()?.getOrNull(1)?.toString()
        val dbTimeMs = spanRepository.aggregateDbTimeByRoute(fromMs, toMs, release)
            .firstOrNull { it[0]?.toString() == route }
            ?.let { (it[1] as Number).toLong() }
        val totalRouteMs = avgMs * totalRequests
        val dbTimeRatio = if (dbTimeMs != null && totalRouteMs > 0) {
            (dbTimeMs / totalRouteMs).coerceIn(0.0, 1.0)
        } else null

        return EndpointDetailMetricsDto(
            route = route,
            httpMethod = httpMethod,
            totalRequests = totalRequests,
            totalErrors = totalErrors,
            errorRate = if (totalRequests > 0) totalErrors.toDouble() / totalRequests else 0.0,
            avgMs = avgMs,
            p50Ms = percentile(durations, 0.50),
            p95Ms = percentile(durations, 0.95),
            p99Ms = percentile(durations, 0.99),
            maxMs = durations.lastOrNull() ?: 0L,
            dbTimeMs = dbTimeMs,
            dbTimeRatio = dbTimeRatio
        )
    }

    private fun buildSampleTraces(
        route: String,
        fromMs: Long,
        toMs: Long,
        release: String?
    ): List<TraceSummaryDto> {
        val roots = spanRepository.searchRootSpans(
            fromMs, toMs, route, null, null, null, null, release, PageRequest.of(0, 10)
        ).content
        val traceIds = roots.mapNotNull { it.traceId }
        val aggregates = if (traceIds.isNotEmpty()) {
            spanRepository.aggregateByTraceIds(traceIds).associate {
                (it[0]?.toString() ?: "") to Pair((it[1] as Number).toLong(), (it[2] as Number).toLong())
            }
        } else emptyMap()
        return roots.map { root -> toTraceSummary(root, aggregates[root.traceId]) }
    }

    private fun toTraceSummary(root: ObsSpan, agg: Pair<Long, Long>?): TraceSummaryDto {
        val spanCount = agg?.first ?: 1L
        val errorCount = agg?.second ?: 0L
        return TraceSummaryDto(
            traceId = root.traceId,
            rootName = root.name,
            rootSpanId = root.spanId,
            platform = root.platform,
            sessionId = root.sessionId,
            httpMethod = root.httpMethod,
            httpRoute = root.httpRoute,
            httpStatus = root.httpStatus,
            status = root.status,
            startEpochMs = root.startEpochMs,
            durationMs = root.durationMs,
            spanCount = spanCount,
            hasError = errorCount > 0 || root.status == "ERROR",
            release = root.release
        )
    }

    private fun percentile(sortedAsc: List<Long>, p: Double): Long {
        if (sortedAsc.isEmpty()) return 0L
        val index = (Math.ceil(p * sortedAsc.size).toInt()).coerceIn(1, sortedAsc.size) - 1
        return sortedAsc[index]
    }
}
