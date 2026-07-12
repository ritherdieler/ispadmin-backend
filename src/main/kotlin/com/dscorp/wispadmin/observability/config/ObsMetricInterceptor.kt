package com.dscorp.wispadmin.observability.config

import com.dscorp.wispadmin.observability.service.ObsMetricCollector
import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerInterceptor
import org.springframework.web.servlet.HandlerMapping
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

@Component
class ObsMetricInterceptor(
    private val metricCollector: ObsMetricCollector
) : HandlerInterceptor {

    companion object {
        private const val START_ATTR = "obsMetricStart"
    }

    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        request.setAttribute(START_ATTR, System.currentTimeMillis())
        return true
    }

    override fun afterCompletion(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any,
        ex: Exception?
    ) {
        val start = request.getAttribute(START_ATTR) as? Long ?: return
        val duration = System.currentTimeMillis() - start
        val status = if (ex != null && response.status < 400) 500 else response.status
        metricCollector.record(request.method, resolveRoute(request), duration, status)
    }

    private fun resolveRoute(request: HttpServletRequest): String {
        val pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE) as? String
        if (!pattern.isNullOrBlank()) return pattern
        return request.servletPath ?: request.requestURI ?: "unknown"
    }
}
