package com.dscorp.wispadmin.observability.config

import com.dscorp.wispadmin.wispadmin.security.ObservabilitySessionTokenService
import org.springframework.http.HttpStatus
import org.springframework.http.server.ServerHttpRequest
import org.springframework.http.server.ServerHttpResponse
import org.springframework.stereotype.Component
import org.springframework.web.socket.WebSocketHandler
import org.springframework.web.socket.server.HandshakeInterceptor
import org.springframework.web.util.UriComponentsBuilder

@Component
class ObservabilityWebSocketHandshakeInterceptor(
    private val sessionTokenService: ObservabilitySessionTokenService
) : HandshakeInterceptor {

    companion object {
        const val QUERY_PARAM = "obsToken"
        const val PLATFORM = "dashboard"
        const val SESSION_PLATFORM_ATTRIBUTE = "obsPlatform"
    }

    override fun beforeHandshake(
        request: ServerHttpRequest,
        response: ServerHttpResponse,
        wsHandler: WebSocketHandler,
        attributes: MutableMap<String, Any>
    ): Boolean {
        val token = queryToken(request)
        val claims = sessionTokenService.verifyAdmin(token)
        if (claims == null) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED)
            return false
        }
        attributes[SESSION_PLATFORM_ATTRIBUTE] = PLATFORM
        return true
    }

    override fun afterHandshake(
        request: ServerHttpRequest,
        response: ServerHttpResponse,
        wsHandler: WebSocketHandler,
        exception: Exception?
    ) {
    }

    private fun queryToken(request: ServerHttpRequest): String? =
        UriComponentsBuilder.fromUri(request.uri).build().queryParams.getFirst(QUERY_PARAM)
}
