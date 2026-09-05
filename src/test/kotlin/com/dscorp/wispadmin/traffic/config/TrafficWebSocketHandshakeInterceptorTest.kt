package com.dscorp.wispadmin.traffic.config

import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.http.server.ServletServerHttpRequest
import org.springframework.http.server.ServletServerHttpResponse
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.web.socket.WebSocketHandler
import java.time.Instant
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class TrafficWebSocketHandshakeInterceptorTest {

    private val objectMapper = ObjectMapper()

    @Test
    fun acceptsValidAccessToken() {
        val interceptor = TrafficWebSocketHandshakeInterceptor(
            sessionSecret = "test-secret-1234567890",
            objectMapper = objectMapper,
        )
        val attributes = mutableMapOf<String, Any>()
        val request = ServletServerHttpRequest(
            MockHttpServletRequest("GET", "/ws").apply {
                queryString = "token=${issueToken("test-secret-1234567890", "ADMIN")}"
                requestURI = "/ws"
            }
        )
        val servletResponse = MockHttpServletResponse()
        val response = ServletServerHttpResponse(servletResponse)

        val allowed = interceptor.beforeHandshake(
            request,
            response,
            mockk<WebSocketHandler>(),
            attributes,
        )

        assertTrue(allowed as Boolean)
        assertEquals(1, attributes[TrafficWebSocketHandshakeInterceptor.SESSION_USER_ID_ATTRIBUTE])
        assertEquals("ADMIN", attributes[TrafficWebSocketHandshakeInterceptor.SESSION_USER_TYPE_ATTRIBUTE])
    }

    @Test
    fun rejectsMissingToken() {
        val interceptor = TrafficWebSocketHandshakeInterceptor(
            sessionSecret = "test-secret-1234567890",
            objectMapper = objectMapper,
        )
        val servletResponse = MockHttpServletResponse()
        val response = ServletServerHttpResponse(servletResponse)

        val allowed = interceptor.beforeHandshake(
            ServletServerHttpRequest(MockHttpServletRequest("GET", "/ws")),
            response,
            mockk<WebSocketHandler>(),
            mutableMapOf(),
        )

        assertFalse(allowed as Boolean)
        assertEquals(HttpStatus.UNAUTHORIZED.value(), servletResponse.status)
    }

    private fun issueToken(secret: String, type: String): String {
        val claims = mapOf(
            "userId" to 1,
            "username" to "dscorp",
            "type" to type,
            "iat" to Instant.now().epochSecond,
            "exp" to Instant.now().plusSeconds(600).epochSecond,
            "typ" to "access",
        )
        val encoder = Base64.getUrlEncoder().withoutPadding()
        val payload = encoder.encodeToString(objectMapper.writeValueAsBytes(claims))
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        val signature = encoder.encodeToString(mac.doFinal(payload.toByteArray(Charsets.UTF_8)))
        return "$payload.$signature"
    }
}
