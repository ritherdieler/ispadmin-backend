package com.dscorp.wispadmin.traffic.websocket

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import org.springframework.web.socket.messaging.SessionDisconnectEvent

@Component
class TrafficWebSocketEventListener(
    private val sessionCleanups: ObjectProvider<TrafficWebSocketSessionCleanup>
) {
    private val logger = LoggerFactory.getLogger(TrafficWebSocketEventListener::class.java)

    @EventListener
    fun handleWebSocketDisconnectListener(event: SessionDisconnectEvent) {
        val sessionId = event.sessionId
        logger.info("Traffic websocket disconnected sessionId={}", sessionId)
        sessionCleanups.forEach { it.handleUserDisconnect(sessionId) }
    }
}
