package com.dscorp.wispadmin.observability.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class EventIngestBatchRequest(
    val events: List<EventIngestRequest> = emptyList()
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class EventIngestRequest(
    val eventType: String? = null,
    val platform: String? = null,
    val severity: String? = null,
    val message: String? = null,
    val errorType: String? = null,
    val stacktrace: String? = null,
    val environment: String? = null,
    val release: String? = null,
    val correlationId: String? = null,
    val sessionId: String? = null,
    val url: String? = null,
    val httpMethod: String? = null,
    val httpStatus: Int? = null,
    val durationMs: Long? = null,
    val userAgent: String? = null,
    val user: Map<String, Any?>? = null,
    val device: Map<String, Any?>? = null,
    val breadcrumbs: List<Any?>? = null,
    val tags: Map<String, Any?>? = null,
    val context: Map<String, Any?>? = null,
    val replayId: Long? = null,
    val timestamp: Long? = null
)

data class EventIngestResponse(
    val accepted: Int,
    val rejected: Int,
    val issueIds: List<Long>
)
