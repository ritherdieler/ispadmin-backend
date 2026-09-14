package com.dscorp.wispadmin.observability.config

import com.dscorp.wispadmin.observability.entity.ObsSpan
import com.dscorp.wispadmin.observability.service.ObsSpanCollector
import com.dscorp.wispadmin.observability.tracing.TraceContext
import com.dscorp.wispadmin.observability.tracing.TraceIds
import com.dscorp.wispadmin.observability.tracing.TraceParent
import com.dscorp.wispadmin.observability.tracing.TraceScope
import com.dscorp.wispadmin.wispadmin.config.CorrelationIdFilter
import com.dscorp.wispadmin.wispadmin.tracing.TraceContext as HttpTraceContext
import org.slf4j.MDC
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerMapping
import java.util.concurrent.ThreadLocalRandom
import javax.servlet.FilterChain
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import org.springframework.web.filter.OncePerRequestFilter

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 15)
class TraceContextFilter(
    private val spanCollector: ObsSpanCollector,
    private val properties: ObservabilityProperties
) : OncePerRequestFilter() {

    companion object {
        const val HEADER_TRACEPARENT = HttpTraceContext.HEADER_TRACEPARENT
        const val HEADER_SESSION = HttpTraceContext.HEADER_SESSION
        const val ATTRIBUTE_SESSION = HttpTraceContext.ATTRIBUTE_SESSION
        const val ATTRIBUTE_TRACE_ID = HttpTraceContext.ATTRIBUTE_TRACE_ID
        const val MDC_KEY = HttpTraceContext.MDC_KEY
    }

    internal var clock: () -> Long = { System.currentTimeMillis() }

    internal var randomSupplier: () -> Double = { ThreadLocalRandom.current().nextDouble() }

    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        if (!properties.enabled || !properties.tracing.enabled) return true
        if ("OPTIONS".equals(request.method, ignoreCase = true)) return true
        val servletPath = request.servletPath.orEmpty()
        val requestUri = request.requestURI.orEmpty()
        return servletPath.contains("/observability") || requestUri.contains("/observability")
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val parsed = TraceParent.parse(request.getHeader(HEADER_TRACEPARENT))
        val traceId = parsed?.traceId ?: TraceIds.traceId()
        val parentSpanId = parsed?.parentSpanId
        val serverSpanId = TraceIds.spanId()
        val sessionId = request.getHeader(HEADER_SESSION)?.takeIf { it.isNotBlank() }
        val sampled = decideSampled(parsed?.sampled)

        val scope = TraceScope(
            traceId = traceId,
            currentSpanId = serverSpanId,
            sessionId = sessionId,
            platform = "backend",
            environment = null,
            release = properties.release.takeIf { it.isNotBlank() },
            sampled = sampled
        )
        TraceContext.set(scope)

        request.setAttribute(CorrelationIdFilter.ATTRIBUTE, traceId)
        request.setAttribute(ATTRIBUTE_TRACE_ID, traceId)
        sessionId?.let { request.setAttribute(ATTRIBUTE_SESSION, it) }
        response.setHeader(CorrelationIdFilter.HEADER, traceId)
        response.setHeader(HEADER_TRACEPARENT, TraceParent.format(traceId, serverSpanId, sampled))
        MDC.put(CorrelationIdFilter.MDC_KEY, traceId)
        MDC.put(MDC_KEY, traceId)

        val start = clock()
        var thrown: Exception? = null
        try {
            filterChain.doFilter(request, response)
        } catch (e: Exception) {
            thrown = e
            throw e
        } finally {
            val duration = clock() - start
            val status = if (thrown != null && response.status < 400) 500 else response.status
            if (shouldRetainServerSpan(sampled, status, duration)) {
                enqueueServerSpan(request, traceId, serverSpanId, parentSpanId, sessionId, start, duration, status)
            }
            TraceContext.clear()
            MDC.remove(MDC_KEY)
            MDC.remove(CorrelationIdFilter.MDC_KEY)
        }
    }

    private fun decideSampled(upstreamSampled: Boolean?): Boolean {
        if (upstreamSampled == false) return false
        val rate = properties.tracing.sampleRate
        if (rate >= 1.0) return true
        if (rate <= 0.0) return false
        return randomSupplier() < rate
    }

    private fun shouldRetainServerSpan(sampled: Boolean, status: Int, duration: Long): Boolean {
        if (sampled) return true
        if (status >= 400) return true
        val threshold = properties.tracing.alwaysSampleAboveMs
        return threshold > 0 && duration >= threshold
    }

    private fun enqueueServerSpan(
        request: HttpServletRequest,
        traceId: String,
        serverSpanId: String,
        parentSpanId: String?,
        sessionId: String?,
        start: Long,
        duration: Long,
        status: Int
    ) {
        val route = resolveRoute(request)
        spanCollector.enqueue(
            ObsSpan(
                traceId = traceId,
                spanId = serverSpanId,
                parentSpanId = parentSpanId,
                name = "${request.method} $route".take(500),
                kind = "SERVER",
                platform = "backend",
                sessionId = sessionId,
                startEpochMs = start,
                durationMs = duration,
                status = if (status >= 400) "ERROR" else "OK",
                httpMethod = request.method,
                httpRoute = route,
                httpStatus = status,
                release = properties.release.takeIf { it.isNotBlank() }
            )
        )
    }

    private fun resolveRoute(request: HttpServletRequest): String {
        val pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE) as? String
        if (!pattern.isNullOrBlank()) return pattern
        return request.servletPath ?: request.requestURI ?: "unknown"
    }
}
