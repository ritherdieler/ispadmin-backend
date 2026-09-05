package com.dscorp.wispadmin.traffic.config

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.server.ServerHttpRequest
import org.springframework.http.server.ServerHttpResponse
import org.springframework.stereotype.Component
import org.springframework.web.socket.WebSocketHandler
import org.springframework.web.socket.server.HandshakeInterceptor
import org.springframework.web.util.UriComponentsBuilder
import java.security.MessageDigest
import java.time.Instant
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@Component
class TrafficWebSocketHandshakeInterceptor(
    @Value("\${observability.session.secret:}") private val sessionSecret: String,
    private val objectMapper: ObjectMapper,
) : HandshakeInterceptor {

    companion object {
        const val QUERY_PARAM = "token"
        const val SESSION_USER_ID_ATTRIBUTE = "authUserId"
        const val SESSION_USER_TYPE_ATTRIBUTE = "authUserType"
        private const val HMAC_ALGORITHM = "HmacSHA256"
        private val urlDecoder: Base64.Decoder = Base64.getUrlDecoder()
    }

    override fun beforeHandshake(
        request: ServerHttpRequest,
        response: ServerHttpResponse,
        wsHandler: WebSocketHandler,
        attributes: MutableMap<String, Any>,
    ): Boolean {
        val claims = verifyAccess(queryToken(request))
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
        exception: Exception?,
    ) {
    }

    private fun queryToken(request: ServerHttpRequest): String? =
        UriComponentsBuilder.fromUri(request.uri).build().queryParams.getFirst(QUERY_PARAM)

    private fun verifyAccess(token: String?): TrafficSessionClaims? {
        val claims = verify(token) ?: return null
        return if (claims.typ == null || claims.typ == "access") claims else null
    }

    private fun verify(token: String?): TrafficSessionClaims? {
        if (token.isNullOrBlank() || sessionSecret.isBlank()) return null
        val parts = token.split(".")
        if (parts.size != 2) return null
        val encodedPayload = parts[0]
        val providedSignature = try {
            urlDecoder.decode(parts[1])
        } catch (_: IllegalArgumentException) {
            return null
        }
        val expectedSignature = sign(encodedPayload, sessionSecret)
        if (!MessageDigest.isEqual(expectedSignature, providedSignature)) return null
        val claims = try {
            objectMapper.readValue(urlDecoder.decode(encodedPayload), TrafficSessionClaims::class.java)
        } catch (_: Exception) {
            return null
        }
        return claims.takeIf { it.exp > Instant.now().epochSecond }
    }

    private fun sign(data: String, secret: String): ByteArray {
        val mac = Mac.getInstance(HMAC_ALGORITHM)
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), HMAC_ALGORITHM))
        return mac.doFinal(data.toByteArray(Charsets.UTF_8))
    }
}

data class TrafficSessionClaims(
    val userId: Int? = null,
    val username: String? = null,
    val type: String? = null,
    val iat: Long = 0,
    val exp: Long = 0,
    val typ: String? = null,
)
