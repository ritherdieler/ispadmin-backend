package com.dscorp.wispadmin.observability.tracing

import com.dscorp.wispadmin.observability.config.ObservabilityProperties
import com.dscorp.wispadmin.observability.config.TraceContextFilter
import com.dscorp.wispadmin.observability.entity.ObsSpan
import com.dscorp.wispadmin.observability.service.ObsSpanCollector
import org.springframework.http.HttpRequest
import org.springframework.http.client.ClientHttpRequestExecution
import org.springframework.http.client.ClientHttpRequestInterceptor
import org.springframework.http.client.ClientHttpResponse
import com.dscorp.wispadmin.wispadmin.tracing.TracingInterceptorHolder
import org.springframework.stereotype.Component

@Component
class TracingClientHttpRequestInterceptor(
    private val collector: ObsSpanCollector,
    private val properties: ObservabilityProperties
) : ClientHttpRequestInterceptor {

    override fun intercept(
        request: HttpRequest,
        body: ByteArray,
        execution: ClientHttpRequestExecution
    ): ClientHttpResponse {
        val scope = TraceContext.current()
        if (scope == null || !properties.tracing.enabled) {
            return execution.execute(request, body)
        }

        val spanId = TraceIds.spanId()
        val parentSpanId = scope.currentSpanId
        request.headers.set(TraceContextFilter.HEADER_TRACEPARENT, TraceParent.format(scope.traceId, spanId))
        scope.sessionId?.let { request.headers.set(TraceContextFilter.HEADER_SESSION, it) }

        val start = System.currentTimeMillis()
        var status = "OK"
        var httpStatus: Int? = null
        try {
            val response = execution.execute(request, body)
            httpStatus = runCatching { response.rawStatusCode }.getOrNull()
            if (httpStatus != null && httpStatus >= 400) status = "ERROR"
            return response
        } catch (e: Exception) {
            status = "ERROR"
            throw e
        } finally {
            val duration = System.currentTimeMillis() - start
            val method = request.method?.name ?: "HTTP"
            val host = runCatching { request.uri.host }.getOrNull() ?: ""
            collector.enqueue(
                ObsSpan(
                    traceId = scope.traceId,
                    spanId = spanId,
                    parentSpanId = parentSpanId,
                    name = "$method $host".trim().take(500),
                    kind = "CLIENT",
                    platform = scope.platform,
                    sessionId = scope.sessionId,
                    startEpochMs = start,
                    durationMs = duration,
                    status = status,
                    httpMethod = method,
                    httpRoute = runCatching { request.uri.toString() }.getOrNull()?.take(500),
                    httpStatus = httpStatus,
                    environment = scope.environment,
                    release = scope.release
                )
            )
        }
    }
}


@Component
class TracingInterceptorRegistrar(
    interceptor: TracingClientHttpRequestInterceptor
) {
    init {
        TracingInterceptorHolder.instance = interceptor
    }
}
