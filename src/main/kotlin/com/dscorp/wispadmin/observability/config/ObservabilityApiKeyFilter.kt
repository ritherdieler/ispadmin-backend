package com.dscorp.wispadmin.observability.config

import com.dscorp.wispadmin.wispadmin.security.ObservabilitySessionTokenService
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import javax.servlet.FilterChain
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
class ObservabilityApiKeyFilter(
    private val properties: ObservabilityProperties,
    private val objectMapper: ObjectMapper,
    private val sessionTokenService: ObservabilitySessionTokenService
) : OncePerRequestFilter() {

    companion object {
        const val HEADER = "X-Obs-Api-Key"
        const val SESSION_HEADER = "X-Obs-Session"
        const val PLATFORM_ATTRIBUTE = "obsPlatform"
        const val SESSION_CLAIMS_ATTRIBUTE = "obsSessionClaims"

        internal fun observabilityPath(request: HttpServletRequest): String {
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
        if (isTrackerWebhookPath(request)) return true
        if (isObservabilityWebSocketPath(request)) return true
        return !isObservabilityPath(request)
    }

    private fun isTrackerWebhookPath(request: HttpServletRequest): Boolean {
        val path = observabilityPath(request)
        return path.contains("/observability/tracker/") && path.endsWith("/webhook")
    }

    private fun isObservabilityWebSocketPath(request: HttpServletRequest): Boolean {
        val path = observabilityPath(request)
        val uri = request.requestURI ?: ""
        return path.contains("/ws/observability") || uri.contains("/ws/observability")
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        if (isTelemetryIngestPath(request)) {
            val key = request.getHeader(HEADER)
            if (!properties.isValidApiKey(key)) {
                writeUnauthorized(response, "Missing or invalid $HEADER header")
                return
            }
            properties.platformForApiKey(key)?.let { request.setAttribute(PLATFORM_ATTRIBUTE, it) }
            filterChain.doFilter(request, response)
            return
        }

        if (isReleasesReadPath(request)) {
            val key = request.getHeader(HEADER)
            if (properties.isValidApiKey(key)) {
                properties.platformForApiKey(key)?.let { request.setAttribute(PLATFORM_ATTRIBUTE, it) }
                filterChain.doFilter(request, response)
                return
            }
        }

        val token = request.getHeader(SESSION_HEADER)
        val claims = sessionTokenService.verifyAdmin(token)
        if (claims == null) {
            writeUnauthorized(response, "Missing or invalid $SESSION_HEADER token")
            return
        }
        request.setAttribute(SESSION_CLAIMS_ATTRIBUTE, claims)
        filterChain.doFilter(request, response)
    }

    private fun isTelemetryIngestPath(request: HttpServletRequest): Boolean {
        if (!"POST".equals(request.method, ignoreCase = true)) return false
        val path = observabilityPath(request)
        return path.endsWith("/observability/events") ||
            path.endsWith("/observability/spans") ||
            path.endsWith("/observability/rum") ||
            path.endsWith("/observability/replays") ||
            path.endsWith("/observability/releases") ||
            path.endsWith("/observability/symbols/sourcemaps") ||
            path.endsWith("/observability/symbols/proguard")
    }

    private fun isReleasesReadPath(request: HttpServletRequest): Boolean {
        if (!"GET".equals(request.method, ignoreCase = true)) return false
        val path = observabilityPath(request)
        return path.endsWith("/observability/releases")
    }

    private fun isObservabilityPath(request: HttpServletRequest): Boolean {
        val path = observabilityPath(request)
        val uri = request.requestURI ?: ""
        return path.startsWith("/observability") ||
            path.contains("/observability/") ||
            uri.contains("/observability/")
    }

    private fun writeUnauthorized(response: HttpServletResponse, message: String) {
        response.status = HttpStatus.UNAUTHORIZED.value()
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        val body = mapOf(
            "error" to "unauthorized",
            "message" to message
        )
        response.writer.write(objectMapper.writeValueAsString(body))
    }
}
