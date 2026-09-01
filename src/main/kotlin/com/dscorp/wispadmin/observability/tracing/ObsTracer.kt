package com.dscorp.wispadmin.observability.tracing

import com.dscorp.wispadmin.observability.config.ObservabilityProperties
import com.dscorp.wispadmin.observability.entity.ObsSpan
import com.dscorp.wispadmin.observability.service.ObsSpanCollector
import com.dscorp.wispadmin.wispadmin.tracing.Tracer
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Component

@Component
@Primary
class ObsTracer(
    private val collector: ObsSpanCollector,
    private val properties: ObservabilityProperties,
    private val objectMapper: ObjectMapper
) : Tracer {

    override fun <T> span(name: String, kind: String, tags: Map<String, Any?>?, block: () -> T): T {
        val scope = TraceContext.current()
        if (scope == null || !properties.tracing.enabled) return block()

        val spanId = TraceIds.spanId()
        val parentSpanId = scope.currentSpanId
        val start = System.currentTimeMillis()
        var status = "OK"
        scope.currentSpanId = spanId
        try {
            return block()
        } catch (e: Throwable) {
            status = "ERROR"
            throw e
        } finally {
            val duration = System.currentTimeMillis() - start
            scope.currentSpanId = parentSpanId
            collector.enqueue(
                ObsSpan(
                    traceId = scope.traceId,
                    spanId = spanId,
                    parentSpanId = parentSpanId,
                    name = name.take(500),
                    kind = kind,
                    platform = scope.platform,
                    sessionId = scope.sessionId,
                    startEpochMs = start,
                    durationMs = duration,
                    status = status,
                    tagsJson = toJson(tags),
                    environment = scope.environment,
                    release = scope.release
                )
            )
        }
    }

    fun recordDbSpan(statement: String, elapsedMs: Long, success: Boolean) {
        val scope = TraceContext.current() ?: return
        if (!properties.tracing.enabled) return
        val start = System.currentTimeMillis() - elapsedMs
        collector.enqueue(
            ObsSpan(
                traceId = scope.traceId,
                spanId = TraceIds.spanId(),
                parentSpanId = scope.currentSpanId,
                name = dbSpanName(statement),
                kind = "DB",
                platform = scope.platform,
                sessionId = scope.sessionId,
                startEpochMs = start,
                durationMs = elapsedMs,
                status = if (success) "OK" else "ERROR",
                dbStatement = statement.take(2000),
                environment = scope.environment,
                release = scope.release
            )
        )
    }

    private fun dbSpanName(statement: String): String {
        val normalized = statement.trim().replace(Regex("\\s+"), " ")
        val verb = normalized.substringBefore(' ').uppercase()
        return "db: ${verb.take(20)}".take(500)
    }

    private fun toJson(value: Any?): String? {
        if (value == null) return null
        return try {
            objectMapper.writeValueAsString(value)
        } catch (e: Exception) {
            null
        }
    }
}
