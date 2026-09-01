package com.dscorp.wispadmin.wispadmin.security

import org.springframework.http.HttpStatus
import org.springframework.http.server.ServerHttpRequest
import org.springframework.http.server.ServerHttpResponse
import org.springframework.stereotype.Component
import org.springframework.web.socket.WebSocketHandler
import org.springframework.web.socket.server.HandshakeInterceptor
import org.springframework.web.util.UriComponentsBuilder

@Component
class PlatformWebSocketHandshakeInterceptor(
    private val sessionTokenService: ObservabilitySessionTokenService
) : HandshakeInterceptor {

    companion object {
        const val QUERY_PARAM = "token"
        const val SESSION_USER_ID_ATTRIBUTE = "authUserId"
        const val SESSION_USER_TYPE_ATTRIBUTE = "authUserType"
    }

    override fun beforeHandshake(
        request: ServerHttpRequest,
        response: ServerHttpResponse,
        wsHandler: WebSocketHandler,
        attributes: MutableMap<String, Any>
    ): Boolean {
        val token = queryToken(request)
        val claims = sessionTokenService.verifyAccess(token)
        if (claims == null) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED)
            return false
        }
        claims.userId?.let { attributes[SESSION_USER_ID_ATTRIBUTE] = it }
        claims.type?.let { attributes[SESSION_USER_TYPE_ATTRIBUTE] = it }
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
