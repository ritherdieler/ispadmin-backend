package com.dscorp.wispadmin.observability.dto

import com.dscorp.wispadmin.observability.entity.ObsEvent
import com.dscorp.wispadmin.observability.entity.ObsIssue
import com.dscorp.wispadmin.observability.entity.ObsIssueStatus
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import java.time.LocalDateTime

data class IssueSummaryDto(
    val id: Long?,
    val fingerprint: String,
    val title: String?,
    val platform: String?,
    val severity: String?,
    val status: ObsIssueStatus,
    val errorType: String?,
    val lastMessage: String?,
    val lastEnvironment: String?,
    val lastRelease: String?,
    val eventCount: Long,
    val firstSeen: LocalDateTime?,
    val lastSeen: LocalDateTime?,
    val jiraIssueKey: String?,
    val trackerProvider: String?,
    val trackerIssueKey: String?,
    val trackerBrowseUrl: String?
)

data class EventDto(
    val id: Long?,
    val issueId: Long?,
    val fingerprint: String?,
    val eventType: String?,
    val platform: String?,
    val severity: String?,
    val feature: String?,
    val action: String?,
    val message: String?,
    val errorType: String?,
    val stacktrace: String?,
    val stacktraceSymbolicated: String?,
    val symbolicated: Boolean,
    val environment: String?,
    val release: String?,
    val correlationId: String?,
    val sessionId: String?,
    val url: String?,
    val httpMethod: String?,
    val httpStatus: Int?,
    val durationMs: Long?,
    val userAgent: String?,
    val user: Any?,
    val device: Any?,
    val breadcrumbs: Any?,
    val tags: Any?,
    val context: Any?,
    val replayId: Long?,
    val format: String?,
    val eventTimestamp: LocalDateTime?,
    val createdAt: LocalDateTime?
)

data class PagedResponse<T>(
    val content: List<T>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ChangeStatusRequest(
    val status: ObsIssueStatus? = null
)

data class OverviewStatsDto(
    val openIssues: Long,
    val resolvedIssues: Long,
    val ignoredIssues: Long,
    val eventsLast24h: Long,
    val eventsLastHour: Long,
    val issuesByPlatform: Map<String, Long>,
    val openIssuesBySeverity: Map<String, Long>,
    val eventsByFeature: Map<String, Long>,
    val topIssues: List<IssueSummaryDto>,
    val tracesLastHour: Long,
    val errorTraceRate: Double,
    val p95TraceDurationMs: Long,
    val slowTraces: List<TraceSummaryDto>
)

data class DbQueryAggregateDto(
    val statement: String?,
    val calls: Long,
    val totalMs: Long,
    val avgMs: Double,
    val maxMs: Long,
    val pct: Double
)

data class NPlusOneCandidateDto(
    val statement: String?,
    val exampleTraceId: String?,
    val maxRepetitions: Long,
    val totalMs: Long,
    val affectedTraces: Long
)

data class SessionSummaryDto(
    val sessionId: String?,
    val platform: String?,
    val eventCount: Long,
    val traceCount: Long,
    val hasReplay: Boolean,
    val firstSeen: LocalDateTime?,
    val lastSeen: LocalDateTime?,
    val user: Any?
)

data class ReplaySummaryDto(
    val id: Long?,
    val format: String?,
    val durationMs: Long?,
    val sizeBytes: Long?,
    val createdAt: LocalDateTime?
)

data class SessionDetailDto(
    val summary: SessionSummaryDto,
    val events: List<EventDto>,
    val traces: List<TraceSummaryDto>,
    val replays: List<ReplaySummaryDto>
)

data class TimeSeriesPointDto(
    val bucket: String,
    val platform: String?,
    val count: Long
)

fun ObsIssue.toSummaryDto() = IssueSummaryDto(
    id = id,
    fingerprint = fingerprint,
    title = title,
    platform = platform,
    severity = severity,
    status = status,
    errorType = errorType,
    lastMessage = lastMessage,
    lastEnvironment = lastEnvironment,
    lastRelease = lastRelease,
    eventCount = eventCount,
    firstSeen = firstSeen,
    lastSeen = lastSeen,
    jiraIssueKey = jiraIssueKey,
    trackerProvider = trackerProvider,
    trackerIssueKey = trackerIssueKey,
    trackerBrowseUrl = trackerBrowseUrl
)

fun ObsEvent.toDto(rawJson: (String?) -> Any?, format: String? = null) = EventDto(
    id = id,
    issueId = issueId,
    fingerprint = fingerprint,
    eventType = eventType,
    platform = platform,
    severity = severity,
    feature = feature,
    action = action,
    message = message,
    errorType = errorType,
    stacktrace = stacktrace,
    stacktraceSymbolicated = stacktraceSymbolicated,
    symbolicated = !stacktraceSymbolicated.isNullOrBlank(),
    environment = environment,
    release = release,
    correlationId = correlationId,
    sessionId = sessionId,
    url = url,
    httpMethod = httpMethod,
    httpStatus = httpStatus,
    durationMs = durationMs,
    userAgent = userAgent,
    user = rawJson(userJson),
    device = rawJson(deviceJson),
    breadcrumbs = rawJson(breadcrumbsJson),
    tags = rawJson(tagsJson),
    context = rawJson(contextJson),
    replayId = replayId,
    format = format,
    eventTimestamp = eventTimestamp,
    createdAt = createdAt
)
