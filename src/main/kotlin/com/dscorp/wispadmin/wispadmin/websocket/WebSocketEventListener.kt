package com.dscorp.wispadmin.wispadmin.websocket

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import org.springframework.web.socket.messaging.SessionDisconnectEvent

@Component
class WebSocketEventListener {
    
    private val logger = LoggerFactory.getLogger(WebSocketEventListener::class.java)
    
    @Autowired
    private lateinit var interfaceTrafficWebSocket: InterfaceTrafficWebSocket
    
    @Autowired
    private lateinit var deviceResourcesWebSocket: DeviceResourcesWebSocket

    @Autowired
    private lateinit var subscriptionTrafficWebSocket: SubscriptionTrafficWebSocket
    
    @EventListener
    fun handleWebSocketDisconnectListener(event: SessionDisconnectEvent) {
        val sessionId = event.sessionId
        logger.info("🔌 WebSocket desconectado - SessionId: $sessionId")
        
        interfaceTrafficWebSocket.handleUserDisconnect(sessionId)
        deviceResourcesWebSocket.handleUserDisconnect(sessionId)
        subscriptionTrafficWebSocket.handleUserDisconnect(sessionId)
    }
} 