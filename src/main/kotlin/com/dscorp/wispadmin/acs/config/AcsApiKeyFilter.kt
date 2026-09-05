package com.dscorp.wispadmin.acs.config

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.filter.OncePerRequestFilter
import javax.servlet.FilterChain
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

class AcsApiKeyFilter(
    private val properties: AcsProperties,
    private val objectMapper: ObjectMapper,
) : OncePerRequestFilter() {

    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        if ("OPTIONS".equals(request.method, ignoreCase = true)) return true
        val path = gatewayPath(request)
        if (path.endsWith("/api/acs/v1/health")) return true
        return !path.startsWith("/api/acs/v1")
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val key = request.getHeader(HEADER)
        if (!properties.isValidApiKey(key)) {
            response.status = HttpStatus.UNAUTHORIZED.value()
            response.contentType = MediaType.APPLICATION_JSON_VALUE
            response.writer.write(
                objectMapper.writeValueAsString(
                    mapOf("error" to "unauthorized", "message" to "Missing or invalid $HEADER header")
                )
            )
            return
        }
        filterChain.doFilter(request, response)
    }

    companion object {
        const val HEADER = "X-Acs-Key"

        fun gatewayPath(request: HttpServletRequest): String {
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
}
