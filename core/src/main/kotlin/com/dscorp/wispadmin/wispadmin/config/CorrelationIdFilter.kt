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
        try {
            filterChain.doFilter(request, response)
        } finally {
            MDC.remove(MDC_KEY)
        }
    }
}
