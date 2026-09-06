package com.dscorp.wispadmin.traffic.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.server.ServerHttpRequest
import org.springframework.http.server.ServerHttpResponse
import org.springframework.stereotype.Component
import org.springframework.web.socket.WebSocketHandler
import org.springframework.web.socket.server.HandshakeInterceptor
import java.security.MessageDigest

@Component
class TrafficWebSocketHandshakeInterceptor(@Value("\${traffic.api-key:}") private val serviceKey: String) : HandshakeInterceptor {
    override fun beforeHandshake(request: ServerHttpRequest,response: ServerHttpResponse,handler: WebSocketHandler,attributes: MutableMap<String,Any>): Boolean {
        val supplied=request.headers.getFirst("X-Traffic-Key")
        val allowed=serviceKey.isNotBlank() && !supplied.isNullOrBlank() && MessageDigest.isEqual(serviceKey.toByteArray(),supplied.toByteArray())
        if(!allowed) response.setStatusCode(HttpStatus.UNAUTHORIZED)
        return allowed
    }
    override fun afterHandshake(request: ServerHttpRequest,response: ServerHttpResponse,handler: WebSocketHandler,exception: Exception?) = Unit
}
