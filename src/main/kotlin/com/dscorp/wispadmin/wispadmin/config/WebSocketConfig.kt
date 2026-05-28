package com.dscorp.wispadmin.wispadmin.config

import org.springframework.context.annotation.Configuration
import org.springframework.messaging.simp.config.MessageBrokerRegistry
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker
import org.springframework.web.socket.config.annotation.StompEndpointRegistry
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer

@Configuration
@EnableWebSocketMessageBroker
class WebSocketConfig : WebSocketMessageBrokerConfigurer {

    override fun configureMessageBroker(registry: MessageBrokerRegistry) {
        // Configurar el broker de mensajes para enviar mensajes a los clientes
        registry.enableSimpleBroker("/topic")
        
        // Configurar el prefijo para los mensajes que van al servidor
        registry.setApplicationDestinationPrefixes("/app")
    }

    override fun registerStompEndpoints(registry: StompEndpointRegistry) {
        // Registrar el endpoint WebSocket
        registry.addEndpoint("/ws")
            .setAllowedOriginPatterns("*") // Permitir CORS para desarrollo
            .withSockJS() // Habilitar SockJS para compatibilidad con navegadores antiguos
    }
}
