package com.dscorp.wispadmin.wispadmin.websocket

import com.dscorp.wispadmin.wispadmin.security.PlatformWebSocketHandshakeInterceptor
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.messaging.simp.SimpMessageHeaderAccessor
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Controller
import java.time.Instant

data class WhatsAppPresencePayload(
    val phone: String? = null,
    val conversationId: Long? = null,
    val state: String? = null
)

data class WhatsAppPresenceEvent(
    val phone: String?,
    val conversationId: Long?,
    val state: String,
    val agentId: Int?,
    val timestamp: String
)

@Controller
class WhatsAppPresenceWebSocket(
    private val messagingTemplate: SimpMessagingTemplate
) {

    @MessageMapping("/whatsapp/presence")
    fun presence(
        @Payload payload: WhatsAppPresencePayload,
        headerAccessor: SimpMessageHeaderAccessor
    ) {
        val state = payload.state?.trim()?.uppercase().orEmpty()
        if (state !in ALLOWED_STATES) return
        val phone = payload.phone?.trim()?.takeIf { it.isNotEmpty() }
        if (phone == null && payload.conversationId == null) return

        val agentId = headerAccessor.sessionAttributes
            ?.get(PlatformWebSocketHandshakeInterceptor.SESSION_USER_ID_ATTRIBUTE)
            ?.let {
                when (it) {
                    is Int -> it
                    is Number -> it.toInt()
                    else -> it.toString().toIntOrNull()
                }
            }

        messagingTemplate.convertAndSend(
            TOPIC_PRESENCE,
            WhatsAppPresenceEvent(
                phone = phone,
                conversationId = payload.conversationId,
                state = state,
                agentId = agentId,
                timestamp = Instant.now().toString()
            )
        )
    }

    companion object {
        const val TOPIC_PRESENCE = "/topic/whatsapp/presence"
        private val ALLOWED_STATES = setOf("VIEWING", "TYPING", "IDLE")
    }
}
