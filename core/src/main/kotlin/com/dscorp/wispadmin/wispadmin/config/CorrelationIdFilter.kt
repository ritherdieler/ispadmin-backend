package com.dscorp.wispadmin.wispadmin.config

import org.slf4j.MDC
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID
import javax.servlet.FilterChain
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
class CorrelationIdFilter : OncePerRequestFilter() {

    companion object {
        const val HEADER = "X-Correlation-Id"
        const val ATTRIBUTE = "obsCorrelationId"
        const val MDC_KEY = "correlationId"
        const val OPERATION_HEADER = "X-Operation-Id"
        const val OPERATION_MDC_KEY = "operationId"
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val incoming = request.getHeader(HEADER)
        val correlationId = if (incoming.isNullOrBlank()) UUID.randomUUID().toString() else incoming
        request.setAttribute(ATTRIBUTE, correlationId)
        response.setHeader(HEADER, correlationId)
        MDC.put(MDC_KEY, correlationId)
        request.getHeader(OPERATION_HEADER)?.trim()?.takeIf { OPERATION_ID.matches(it) }?.let { MDC.put(OPERATION_MDC_KEY, it) }
        try {
            filterChain.doFilter(request, response)
        } finally {
            MDC.remove(MDC_KEY)
            MDC.remove(OPERATION_MDC_KEY)
        }
    }

    private val OPERATION_ID = Regex("[A-Za-z0-9_-]{8,64}")
}
