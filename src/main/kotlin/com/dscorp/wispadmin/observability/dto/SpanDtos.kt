package com.dscorp.wispadmin.observability.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class SpanIngestRequest(
    val traceId: String? = null,
    val spanId: String? = null,
    val parentSpanId: String? = null,
    val name: String? = null,
    val kind: String? = null,
    val platform: String? = null,
    val sessionId: String? = null,
    val startEpochMs: Long? = null,
    val durationMs: Long? = null,
    val status: String? = null,
    val httpMethod: String? = null,
    val httpRoute: String? = null,
    val httpStatus: Int? = null,
    val dbStatement: String? = null,
    val tagsJson: String? = null,
    val environment: String? = null,
    val release: String? = null
)

data class SpanIngestResponse(
    val accepted: Int,
    val rejected: Int
)

data class TraceSummaryDto(
    val traceId: String?,
    val rootName: String?,
    val rootSpanId: String?,
    val platform: String?,
    val sessionId: String?,
    val httpMethod: String?,
    val httpRoute: String?,
    val httpStatus: Int?,
    val status: String?,
    val startEpochMs: Long?,
    val durationMs: Long?,
    val spanCount: Long,
    val hasError: Boolean
)

data class SpanDto(
    val id: Long?,
    val traceId: String?,
    val spanId: String?,
    val parentSpanId: String?,
    val name: String?,
    val kind: String?,
    val platform: String?,
    val sessionId: String?,
    val startEpochMs: Long?,
    val durationMs: Long?,
    val status: String?,
    val httpMethod: String?,
    val httpRoute: String?,
    val httpStatus: Int?,
    val dbStatement: String?,
    val tags: Any?,
    val environment: String?,
    val release: String?
)

data class TraceDetailDto(
    val traceId: String,
    val spans: List<SpanDto>,
    val events: List<EventDto>
)
