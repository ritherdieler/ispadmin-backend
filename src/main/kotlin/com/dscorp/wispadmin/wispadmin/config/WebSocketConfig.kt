package com.dscorp.wispadmin.wispadmin.config

import com.dscorp.wispadmin.wispadmin.security.PlatformWebSocketHandshakeInterceptor
import org.springframework.beans.factory.ObjectProvider
import org.springframework.context.annotation.Configuration
import org.springframework.messaging.support.ChannelInterceptor
import org.springframework.messaging.simp.config.ChannelRegistration
import org.springframework.messaging.simp.config.MessageBrokerRegistry
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker
import org.springframework.web.socket.config.annotation.StompEndpointRegistry
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer
import org.springframework.web.socket.server.HandshakeInterceptor
import java.util.stream.Collectors

@Configuration
@EnableWebSocketMessageBroker
class WebSocketConfig(
    private val handshakeInterceptors: ObjectProvider<HandshakeInterceptor>,
    private val channelInterceptors: ObjectProvider<ChannelInterceptor>,
    private val platformHandshakeInterceptor: PlatformWebSocketHandshakeInterceptor
) : WebSocketMessageBrokerConfigurer {

    override fun configureMessageBroker(registry: MessageBrokerRegistry) {
        // Configurar el broker de mensajes para enviar mensajes a los clientes
        registry.enableSimpleBroker("/topic")
        
        // Configurar el prefijo para los mensajes que van al servidor
        registry.setApplicationDestinationPrefixes("/app")
    }

    override fun registerStompEndpoints(registry: StompEndpointRegistry) {
        // Endpoint compartido (backoffice: tickets, trafico, recursos)
        registry.addEndpoint("/ws")
            .addInterceptors(platformHandshakeInterceptor)
            .setAllowedOriginPatterns(
                "http://localhost:*",
                "http://127.0.0.1:*",
                "https://backoffice.gigafiberperu.cloud",
                "https://api.gigafiberperu.cloud"
            )
            .withSockJS()

        // Endpoint dedicado de observabilidad: handshake protegido por API key (solo dashboard)
        val extraHandshake = handshakeInterceptors.orderedStream()
            .filter { it !is PlatformWebSocketHandshakeInterceptor }
            .collect(Collectors.toList())
            .toTypedArray()
        registry.addEndpoint("/ws/observability")
            .addInterceptors(*extraHandshake)
            .setAllowedOriginPatterns(
                "http://localhost:5175",
                "https://observability.gigafiberperu.cloud",
                "https://api.gigafiberperu.cloud"
            )
            .withSockJS()
    }

    override fun configureClientInboundChannel(registration: ChannelRegistration) {
        val extras = channelInterceptors.orderedStream().collect(Collectors.toList())
        if (extras.isNotEmpty()) {
            registration.interceptors(*extras.toTypedArray())
        }
    }
}
