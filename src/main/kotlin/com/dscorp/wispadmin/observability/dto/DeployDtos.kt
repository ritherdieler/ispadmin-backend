package com.dscorp.wispadmin.observability.dto

import com.dscorp.wispadmin.observability.entity.ObsDeployEvent
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import java.time.LocalDateTime

data class ReleaseRouteLatencyDto(
    val route: String?,
    val httpMethod: String?,
    val baseAvgMs: Double?,
    val targetAvgMs: Double?,
    val baseP95Ms: Long?,
    val targetP95Ms: Long?,
    val deltaP95Pct: Double?,
    val regressed: Boolean
)

data class ReleaseAdoptionDto(
    val sessionCount: Long,
    val eventCount: Long,
    val traceCount: Long
)

data class ReleaseSummaryDto(
    val version: String,
    val previousVersion: String?,
    val platform: String?,
    val from: LocalDateTime,
    val to: LocalDateTime,
    val newIssues: List<IssueSummaryDto>,
    val newIssuesCount: Long,
    val latencyComparison: List<ReleaseRouteLatencyDto>,
    val webVitals: List<RumMetricAggregateDto>,
    val adoption: ReleaseAdoptionDto
)

data class DeployEventDto(
    val release: String?,
    val platform: String?,
    val semver: String?,
    val gitSha: String?,
    val deployedAt: LocalDateTime?,
    val notes: String?
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class CreateDeployEventRequest(
    val platform: String,
    val release: String,
    val semver: String? = null,
    val gitSha: String? = null,
    val notes: String? = null
)

fun ObsDeployEvent.toDto() = DeployEventDto(
    release = release,
    platform = platform,
    semver = semver,
    gitSha = gitSha,
    deployedAt = deployedAt,
    notes = notes
)
