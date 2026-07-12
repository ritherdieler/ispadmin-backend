package com.dscorp.wispadmin.observability.port

interface ObservabilityReporter {
    fun report(event: ReportedEvent)
}

data class ReportedEvent(
    val eventType: String,
    val platform: String,
    val severity: String,
    val message: String?,
    val errorType: String?,
    val stacktrace: String?,
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
