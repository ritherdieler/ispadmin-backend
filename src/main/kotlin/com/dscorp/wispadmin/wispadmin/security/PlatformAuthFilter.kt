package com.dscorp.wispadmin.wispadmin.security

import com.dscorp.wispadmin.observability.security.ObservabilitySessionTokenService
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
@Order(Ordered.HIGHEST_PRECEDENCE + 18)
class PlatformAuthFilter(
    private val sessionTokenService: ObservabilitySessionTokenService,
    private val objectMapper: ObjectMapper
) : OncePerRequestFilter() {

    companion object {
        const val AUTH_USER_ID_ATTRIBUTE = "authUserId"
        const val AUTH_USER_TYPE_ATTRIBUTE = "authUserType"
        const val AUTH_USERNAME_ATTRIBUTE = "authUsername"
        private const val BEARER_PREFIX = "Bearer "
    }

    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        if ("OPTIONS".equals(request.method, ignoreCase = true)) return true
        val path = (request.servletPath ?: request.requestURI ?: "").trimEnd('/')
        if (path.startsWith("/observability") || path.contains("/observability/")) return true
        if (path.startsWith("/api/olt-gateway") || path.contains("/api/olt-gateway/")) return true
        if (isSwaggerPath(path)) return true
        if (path.contains("/ws")) return true
        return isPublicPath(path)
    }

    private fun isPublicPath(path: String): Boolean {
        if (path.isEmpty()) return true
        if (path.endsWith("/users/login") ||
            path.endsWith("/users/login/face") ||
            path.endsWith("/users/login/face/photo") ||
            path.endsWith("/users/token/refresh")
        ) return true
        if (path.endsWith("/whatsapp/webhook")) return true
        if (path.contains("/izipay/")) return true
        if (path.contains("/actuator")) return true
        if (path.endsWith("/app/check_version")) return true
        if (path.endsWith("/fcm/save-token")) return true
        if (isAttendancePublicPath(path)) return true
        return false
    }

    private fun isSwaggerPath(path: String): Boolean {
        if (path.startsWith("/swagger-ui") || path == "/swagger-ui.html") return true
        if (path.startsWith("/v3/api-docs")) return true
        if (path.startsWith("/webjars")) return true
        return path.contains("/swagger-ui/") || path.contains("/v3/api-docs")
    }

    private fun isAttendancePublicPath(path: String): Boolean {
        if (path.endsWith("/api/face-data/offline-dataset")) return true
        if (!path.contains("/api/face/")) return false
        return path.endsWith("/challenge/start") ||
            path.endsWith("/identify") ||
            path.endsWith("/identify/photo") ||
            path.endsWith("/verify") ||
            path.endsWith("/verify/photo") ||
            path.endsWith("/verify/password") ||
            path.endsWith("/attendance/offline-sync") ||
            path.endsWith("/evidence")
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val header = request.getHeader("Authorization")
        val token = if (header != null && header.startsWith(BEARER_PREFIX, ignoreCase = true)) {
            header.substring(BEARER_PREFIX.length).trim()
        } else {
            null
        }
        val claims = sessionTokenService.verifyAccess(token)
        if (claims == null) {
            writeUnauthorized(response)
            return
        }
        request.setAttribute(AUTH_USER_ID_ATTRIBUTE, claims.userId)
        request.setAttribute(AUTH_USER_TYPE_ATTRIBUTE, claims.type)
        request.setAttribute(AUTH_USERNAME_ATTRIBUTE, claims.username)
        filterChain.doFilter(request, response)
    }

    private fun writeUnauthorized(response: HttpServletResponse) {
        response.status = HttpStatus.UNAUTHORIZED.value()
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        val body = mapOf(
            "error" to "unauthorized",
            "message" to "Missing or invalid Authorization bearer token"
        )
        response.writer.write(objectMapper.writeValueAsString(body))
    }
}
