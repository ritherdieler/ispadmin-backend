package com.dscorp.wispadmin.netdiag.config

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
class NetDiagApiKeyFilter(
    private val properties: NetDiagProperties,
    private val objectMapper: ObjectMapper
) : OncePerRequestFilter() {

    companion object {
        const val HEADER = "X-Netdiag-Key"

        internal fun netDiagPath(request: HttpServletRequest): String {
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
        val path = netDiagPath(request)
        if (path.endsWith("/api/netdiag/health")) return true
        return !isNetDiagPath(request)
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val key = request.getHeader(HEADER)
        if (!properties.isValidApiKey(key)) {
            response.status = HttpStatus.UNAUTHORIZED.value()
            response.contentType = MediaType.APPLICATION_JSON_VALUE
            val body = mapOf(
                "error" to "unauthorized",
                "message" to "Missing or invalid $HEADER header"
            )
            response.writer.write(objectMapper.writeValueAsString(body))
            return
        }
        filterChain.doFilter(request, response)
    }

    private fun isNetDiagPath(request: HttpServletRequest): Boolean {
        val path = netDiagPath(request)
        val uri = request.requestURI ?: ""
        return path.startsWith("/api/netdiag") ||
            path.contains("/api/netdiag/") ||
            uri.contains("/api/netdiag/")
    }
}
