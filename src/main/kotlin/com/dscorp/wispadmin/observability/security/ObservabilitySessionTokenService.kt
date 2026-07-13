package com.dscorp.wispadmin.observability.security

import com.dscorp.wispadmin.observability.config.ObservabilityProperties
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.security.MessageDigest
import java.time.Instant
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

data class ObservabilitySessionClaims(
    val userId: Int?,
    val username: String?,
    val type: String?,
    val iat: Long,
    val exp: Long,
    val typ: String? = null
)

@Service
class ObservabilitySessionTokenService(
    private val properties: ObservabilityProperties,
    private val objectMapper: ObjectMapper
) {

    companion object {
        private val logger = LoggerFactory.getLogger(ObservabilitySessionTokenService::class.java)
        private const val HMAC_ALGORITHM = "HmacSHA256"
        private const val ADMIN_TYPE = "ADMIN"
        const val TYPE_ACCESS = "access"
        const val TYPE_REFRESH = "refresh"
    }

    private val urlEncoder: Base64.Encoder = Base64.getUrlEncoder().withoutPadding()
    private val urlDecoder: Base64.Decoder = Base64.getUrlDecoder()

    fun isConfigured(): Boolean = properties.session.secret.isNotBlank()

    fun issueAccess(userId: Int?, username: String?, type: String?): String? =
        issue(userId, username, type, TYPE_ACCESS, properties.session.ttlMinutes)

    fun issueRefresh(userId: Int?, username: String?, type: String?): String? =
        issue(userId, username, type, TYPE_REFRESH, properties.session.refreshTtlMinutes)

    fun issue(userId: Int?, username: String?, type: String?): String? =
        issueAccess(userId, username, type)

    private fun issue(userId: Int?, username: String?, type: String?, typ: String, ttlMinutes: Long): String? {
        val secret = properties.session.secret
        if (secret.isBlank()) {
            logger.warn("No se puede emitir token de sesión: observability.session.secret no configurado")
            return null
        }
        val now = Instant.now().epochSecond
        val exp = now + ttlMinutes * 60
        val claims = ObservabilitySessionClaims(
            userId = userId,
            username = username,
            type = type,
            iat = now,
            exp = exp,
            typ = typ
        )
        val encodedPayload = urlEncoder.encodeToString(objectMapper.writeValueAsBytes(claims))
        val encodedSignature = urlEncoder.encodeToString(sign(encodedPayload, secret))
        return "$encodedPayload.$encodedSignature"
    }

    fun verify(token: String?): ObservabilitySessionClaims? {
        if (token.isNullOrBlank()) return null
        val secret = properties.session.secret
        if (secret.isBlank()) return null

        val parts = token.split(".")
        if (parts.size != 2) return null
        val encodedPayload = parts[0]
        val providedSignature = try {
            urlDecoder.decode(parts[1])
        } catch (e: IllegalArgumentException) {
            return null
        }

        val expectedSignature = sign(encodedPayload, secret)
        if (!MessageDigest.isEqual(expectedSignature, providedSignature)) return null

        val claims = try {
            objectMapper.readValue(urlDecoder.decode(encodedPayload), ObservabilitySessionClaims::class.java)
        } catch (e: Exception) {
            return null
        }

        if (claims.exp <= Instant.now().epochSecond) return null
        return claims
    }

    fun verifyAccess(token: String?): ObservabilitySessionClaims? {
        val claims = verify(token) ?: return null
        return if (claims.typ == null || claims.typ == TYPE_ACCESS) claims else null
    }

    fun verifyRefresh(token: String?): ObservabilitySessionClaims? {
        val claims = verify(token) ?: return null
        return if (claims.typ == TYPE_REFRESH) claims else null
    }

    fun verifyAdmin(token: String?): ObservabilitySessionClaims? {
        val claims = verifyAccess(token) ?: return null
        return if (claims.type == ADMIN_TYPE) claims else null
    }

    private fun sign(data: String, secret: String): ByteArray {
        val mac = Mac.getInstance(HMAC_ALGORITHM)
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), HMAC_ALGORITHM))
        return mac.doFinal(data.toByteArray(Charsets.UTF_8))
    }
}
