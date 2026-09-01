package com.dscorp.wispadmin.wispadmin.websocket

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import org.springframework.web.socket.messaging.SessionDisconnectEvent

@Component
class WebSocketEventListener(
    private val sessionCleanups: ObjectProvider<WebSocketSessionCleanup>
) {

    private val logger = LoggerFactory.getLogger(WebSocketEventListener::class.java)

    @EventListener
    fun handleWebSocketDisconnectListener(event: SessionDisconnectEvent) {
        val sessionId = event.sessionId
        logger.info("🔌 WebSocket desconectado - SessionId: $sessionId")
        sessionCleanups.forEach { it.handleUserDisconnect(sessionId) }
    }
}
