package com.dscorp.wispadmin.observability.config

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
    private val objectMapper: ObjectMapper
) : OncePerRequestFilter() {

    companion object {
        const val HEADER = "X-Obs-Api-Key"
        const val PLATFORM_ATTRIBUTE = "obsPlatform"

        private val INGESTION_PATH_SUFFIXES = listOf(
            "/observability/events",
            "/observability/spans",
            "/observability/rum",
            "/observability/replays",
            "/observability/symbols/sourcemaps",
            "/observability/symbols/proguard"
        )
    }

    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        if ("OPTIONS".equals(request.method, ignoreCase = true)) return true
        if (isTrackerWebhookPath(request)) return true
        return !isObservabilityPath(request)
    }

    private fun isTrackerWebhookPath(request: HttpServletRequest): Boolean {
        val path = request.servletPath ?: request.requestURI ?: ""
        return path.contains("/observability/tracker/") && path.trimEnd('/').endsWith("/webhook")
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val key = request.getHeader(HEADER)
        if (!properties.isValidApiKey(key)) {
            writeUnauthorized(response)
            return
        }
        val platform = properties.platformForApiKey(key)
        if (!isIngestionRequest(request) && !properties.isReadApiPlatform(platform)) {
            writeForbidden(response)
            return
        }
        platform?.let { request.setAttribute(PLATFORM_ATTRIBUTE, it) }
        filterChain.doFilter(request, response)
    }

    private fun isIngestionRequest(request: HttpServletRequest): Boolean {
        if (!"POST".equals(request.method, ignoreCase = true)) return false
        val path = (request.servletPath ?: request.requestURI ?: "").trimEnd('/')
        return INGESTION_PATH_SUFFIXES.any { path.endsWith(it) }
    }

    private fun isObservabilityPath(request: HttpServletRequest): Boolean {
        val path = request.servletPath ?: request.requestURI ?: ""
        return path.startsWith("/observability") || path.contains("/observability/")
    }

    private fun writeUnauthorized(response: HttpServletResponse) {
        response.status = HttpStatus.UNAUTHORIZED.value()
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        val body = mapOf(
            "error" to "unauthorized",
            "message" to "Missing or invalid X-Obs-Api-Key header"
        )
        response.writer.write(objectMapper.writeValueAsString(body))
    }

    private fun writeForbidden(response: HttpServletResponse) {
        response.status = HttpStatus.FORBIDDEN.value()
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        val body = mapOf(
            "error" to "forbidden",
            "message" to "This API key is not authorized to read observability data"
        )
        response.writer.write(objectMapper.writeValueAsString(body))
    }
}
