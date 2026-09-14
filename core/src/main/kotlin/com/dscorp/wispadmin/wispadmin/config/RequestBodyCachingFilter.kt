package com.dscorp.wispadmin.wispadmin.config

import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.web.util.ContentCachingRequestWrapper
import org.springframework.web.util.ContentCachingResponseWrapper
import java.nio.charset.StandardCharsets
import javax.servlet.FilterChain
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class RequestBodyCachingFilter : OncePerRequestFilter() {

    companion object {
        const val ATTR_RESPONSE_BODY = "cachedResponseBody"
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val wrappedRequest = ContentCachingRequestWrapper(request)
        val wrappedResponse = ContentCachingResponseWrapper(response)

        try {
            filterChain.doFilter(wrappedRequest, wrappedResponse)
        } finally {
            if (wrappedResponse.status >= 400) {
                val bytes = wrappedResponse.contentAsByteArray
                if (bytes.isNotEmpty()) {
                    val body = String(bytes, StandardCharsets.UTF_8)
                    val truncated = if (body.length > 5000) body.substring(0, 5000) + "...[truncated]" else body
                    wrappedRequest.setAttribute(ATTR_RESPONSE_BODY, truncated)
                }
            }
            wrappedResponse.copyBodyToResponse()
        }
    }
}
