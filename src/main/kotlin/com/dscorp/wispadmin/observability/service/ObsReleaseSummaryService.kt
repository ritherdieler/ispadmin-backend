package com.dscorp.wispadmin.observability.service

import com.dscorp.wispadmin.observability.dto.EndpointMetricAggregateDto
import com.dscorp.wispadmin.observability.dto.ReleaseAdoptionDto
import com.dscorp.wispadmin.observability.dto.ReleaseRouteLatencyDto
import com.dscorp.wispadmin.observability.dto.ReleaseSummaryDto
import com.dscorp.wispadmin.observability.dto.toSummaryDto
import com.dscorp.wispadmin.observability.repository.ObsDeployEventRepository
import com.dscorp.wispadmin.observability.repository.ObsEventRepository
import com.dscorp.wispadmin.observability.repository.ObsIssueRepository
import com.dscorp.wispadmin.observability.repository.ObsSpanRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.time.ZoneId

@Service
class ObsReleaseSummaryService(
    private val deployEventRepository: ObsDeployEventRepository,
    private val issueRepository: ObsIssueRepository,
    private val eventRepository: ObsEventRepository,
    private val spanRepository: ObsSpanRepository,
    private val metricQueryService: ObsMetricQueryService,
    private val rumMetricQueryService: ObsRumQueryService,
    @Value("\${app.timezone:America/Lima}") private val appTimezone: String
) {

    private val regressionThresholdPct = 10.0

    fun summary(version: String): ReleaseSummaryDto {
        val deploy = deployEventRepository.findFirstByReleaseOrderByDeployedAtDesc(version)
        val platform = deploy?.platform
        val to = LocalDateTime.now()
        val from = deploy?.deployedAt ?: to.minusHours(24)

        val nextDeploy = deploy?.deployedAt?.let { deployedAt ->
            if (platform != null) {
                deployEventRepository.findFirstByPlatformAndDeployedAtGreaterThanOrderByDeployedAtAsc(platform, deployedAt)
            } else {
                deployEventRepository.findFirstByDeployedAtGreaterThanOrderByDeployedAtAsc(deployedAt)
            }
        }
        val windowTo = nextDeploy?.deployedAt ?: to

        val previousDeploy = deploy?.deployedAt?.let { deployedAt ->
            if (platform != null) {
                deployEventRepository.findFirstByPlatformAndDeployedAtLessThanOrderByDeployedAtDesc(platform, deployedAt)
            } else {
                deployEventRepository.findFirstByDeployedAtLessThanOrderByDeployedAtDesc(deployedAt)
            }
        }
        val previousVersion = previousDeploy?.release

        val newIssues = issueRepository
            .findNewIssuesForRelease(version, from, PageRequest.of(0, 25))
            .map { it.toSummaryDto() }
        val newIssuesCount = issueRepository.countNewIssuesForRelease(version, from)

        val latencyComparison = buildLatencyComparison(
            version, from, windowTo, previousVersion, previousDeploy?.deployedAt, from
        )

        val webVitals = rumMetricQueryService.aggregate(from, windowTo, version)

        val adoption = buildAdoption(version, from, windowTo)

        return ReleaseSummaryDto(
            version = version,
            previousVersion = previousVersion,
            platform = platform,
            from = from,
            to = windowTo,
            newIssues = newIssues,
            newIssuesCount = newIssuesCount,
            latencyComparison = latencyComparison,
            webVitals = webVitals,
            adoption = adoption
        )
    }

    private fun buildLatencyComparison(
        version: String,
        targetFrom: LocalDateTime,
        targetTo: LocalDateTime,
        previousVersion: String?,
        previousFrom: LocalDateTime?,
        previousTo: LocalDateTime
    ): List<ReleaseRouteLatencyDto> {
        val target = metricQueryService.aggregate(targetFrom, targetTo, version)
        val base = if (previousVersion != null && previousFrom != null) {
            metricQueryService.aggregate(previousFrom, previousTo, previousVersion)
        } else emptyList()
        val baseByKey = base.associateBy { keyOf(it) }

        return target.map { targetAgg ->
            val baseAgg = baseByKey[keyOf(targetAgg)]
            val deltaP95Pct = computeDeltaPct(baseAgg?.p95Ms, targetAgg.p95Ms)
            ReleaseRouteLatencyDto(
                route = targetAgg.route,
                httpMethod = targetAgg.httpMethod,
                baseAvgMs = baseAgg?.avgMs,
                targetAvgMs = targetAgg.avgMs,
                baseP95Ms = baseAgg?.p95Ms,
                targetP95Ms = targetAgg.p95Ms,
                deltaP95Pct = deltaP95Pct,
                regressed = deltaP95Pct != null && deltaP95Pct > regressionThresholdPct
            )
        }.sortedByDescending { it.deltaP95Pct ?: Double.NEGATIVE_INFINITY }
    }

    private fun buildAdoption(version: String, from: LocalDateTime, to: LocalDateTime): ReleaseAdoptionDto {
        val zone = ZoneId.of(appTimezone)
        val fromMs = from.atZone(zone).toInstant().toEpochMilli()
        val toMs = to.atZone(zone).toInstant().toEpochMilli()
        return ReleaseAdoptionDto(
            sessionCount = eventRepository.countDistinctSessionsByReleaseBetween(version, from, to),
            eventCount = eventRepository.countByReleaseBetween(version, from, to),
            traceCount = spanRepository.countRootSpansByReleaseBetween(version, fromMs, toMs)
        )
    }

    private fun keyOf(agg: EndpointMetricAggregateDto): Pair<String?, String?> = Pair(agg.route, agg.httpMethod)

    private fun computeDeltaPct(base: Long?, target: Long?): Double? {
        if (base == null || target == null || base == 0L) return null
        return (target - base).toDouble() / base * 100.0
    }
}
