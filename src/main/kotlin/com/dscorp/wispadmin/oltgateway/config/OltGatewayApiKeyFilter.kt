package com.dscorp.wispadmin.oltgateway.config

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.filter.OncePerRequestFilter
import javax.servlet.FilterChain
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

@Order(Ordered.HIGHEST_PRECEDENCE + 25)
class OltGatewayApiKeyFilter(
    private val properties: OltGatewayProperties,
    private val objectMapper: ObjectMapper
) : OncePerRequestFilter() {

    companion object {
        const val HEADER = "X-Olt-Gateway-Key"
        const val SMARTOLT_TOKEN_HEADER = "X-Token"

        internal fun gatewayPath(request: HttpServletRequest): String {
            val servletPath = request.servletPath?.takeIf { it.isNotBlank() }
            val contextPath = request.contextPath.orEmpty()
            val fromUri = request.requestURI
                ?.takeIf { it.isNotBlank() }
                ?.let { uri ->
                    if (contextPath.isNotEmpty() && uri.startsWith(contextPath)) {
                        uri.removePrefix(contextPath).ifBlank { "/" }
                    } else {
                        uri
                    }
                }
            return (servletPath ?: fromUri ?: "").trimEnd('/')
        }
    }

    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        if ("OPTIONS".equals(request.method, ignoreCase = true)) return true
        val path = gatewayPath(request)
        if (path.endsWith("/api/olt-gateway/health")) return true
        return !isGatewayPath(request)
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val key = request.getHeader(HEADER) ?: request.getHeader(SMARTOLT_TOKEN_HEADER)
        if (!properties.isValidApiKey(key)) {
            response.status = HttpStatus.UNAUTHORIZED.value()
            response.contentType = MediaType.APPLICATION_JSON_VALUE
            val body = mapOf(
                "error" to "unauthorized",
                "message" to "Missing or invalid $HEADER (or $SMARTOLT_TOKEN_HEADER) header"
            )
            response.writer.write(objectMapper.writeValueAsString(body))
            return
        }
        filterChain.doFilter(request, response)
    }

    private fun isGatewayPath(request: HttpServletRequest): Boolean {
        val path = gatewayPath(request)
        val uri = request.requestURI ?: ""
        return path.startsWith("/api/olt-gateway") ||
            path.contains("/api/olt-gateway/") ||
            uri.contains("/api/olt-gateway/")
    }
}
