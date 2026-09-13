package com.dscorp.wispadmin.transport

import org.slf4j.MDC
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID
import javax.servlet.FilterChain
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

class RegistrationTimingFilter(
    private val timing: RegistrationTiming,
) : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        if (!timing.isEnabled) {
            filterChain.doFilter(request, response)
            return
        }
        val path = request.requestURI.removePrefix(request.contextPath.orEmpty()).ifBlank { request.requestURI }
        val incomingTrace = request.getHeader(RegistrationTimingSupport.HEADER)
        val shouldLog = RegistrationTimingSupport.isRegistrationPath(request.requestURI) ||
            RegistrationTimingSupport.isRegistrationPath(path) ||
            !incomingTrace.isNullOrBlank()
        if (!shouldLog) {
            filterChain.doFilter(request, response)
            return
        }
        val trace = incomingTrace?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString().replace("-", "").take(16)
        val previous = MDC.get(RegistrationTimingSupport.MDC_TRACE)
        MDC.put(RegistrationTimingSupport.MDC_TRACE, trace)
        response.setHeader(RegistrationTimingSupport.HEADER, trace)
        val start = System.currentTimeMillis()
        try {
            filterChain.doFilter(request, response)
        } finally {
            timing.httpIn(
                method = request.method,
                path = path,
                status = response.status,
                ms = System.currentTimeMillis() - start,
            )
            if (previous.isNullOrBlank()) {
                MDC.remove(RegistrationTimingSupport.MDC_TRACE)
            } else {
                MDC.put(RegistrationTimingSupport.MDC_TRACE, previous)
            }
        }
    }
}
