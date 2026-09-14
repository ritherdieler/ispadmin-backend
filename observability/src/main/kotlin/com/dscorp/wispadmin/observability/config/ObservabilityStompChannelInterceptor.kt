package com.dscorp.wispadmin.observability.config

import org.springframework.messaging.Message
import org.springframework.messaging.MessageChannel
import org.springframework.messaging.simp.stomp.StompCommand
import org.springframework.messaging.simp.stomp.StompHeaderAccessor
import org.springframework.messaging.support.ChannelInterceptor
import org.springframework.stereotype.Component

@Component
class ObservabilityStompChannelInterceptor : ChannelInterceptor {

    companion object {
        private const val OBSERVABILITY_DESTINATION_PREFIX = "/topic/observability"
    }

    override fun preSend(message: Message<*>, channel: MessageChannel): Message<*>? {
        val accessor = StompHeaderAccessor.wrap(message)
        if (accessor.command != StompCommand.SUBSCRIBE) {
            return message
        }
        val destination = accessor.destination ?: return message
        if (!destination.startsWith(OBSERVABILITY_DESTINATION_PREFIX)) {
            return message
        }
        val platform = accessor.sessionAttributes
            ?.get(ObservabilityWebSocketHandshakeInterceptor.SESSION_PLATFORM_ATTRIBUTE)
        if (platform != ObservabilityWebSocketHandshakeInterceptor.PLATFORM) {
            throw IllegalArgumentException("Unauthorized subscription to observability topic")
        }
        return message
    }
}
