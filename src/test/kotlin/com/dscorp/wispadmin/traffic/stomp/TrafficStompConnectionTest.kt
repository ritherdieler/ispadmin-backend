package com.dscorp.wispadmin.traffic.stomp

import com.dscorp.wispadmin.traffic.config.TrafficWebSocketHandshakeInterceptor
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration
import org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.web.server.LocalServerPort
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.messaging.converter.MappingJackson2MessageConverter
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.messaging.handler.annotation.SendTo
import org.springframework.messaging.simp.config.MessageBrokerRegistry
import org.springframework.messaging.simp.stomp.StompFrameHandler
import org.springframework.messaging.simp.stomp.StompHeaders
import org.springframework.messaging.simp.stomp.StompSession
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter
import org.springframework.stereotype.Controller
import org.springframework.test.context.TestPropertySource
import org.springframework.web.socket.WebSocketHttpHeaders
import org.springframework.web.socket.client.standard.StandardWebSocketClient
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker
import org.springframework.web.socket.config.annotation.StompEndpointRegistry
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer
import org.springframework.web.socket.messaging.WebSocketStompClient
import java.lang.reflect.Type
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

@SpringBootApplication(
    scanBasePackages = ["com.dscorp.wispadmin.traffic.stomp"],
    exclude = [
        DataSourceAutoConfiguration::class,
        HibernateJpaAutoConfiguration::class,
        FlywayAutoConfiguration::class,
        RedisAutoConfiguration::class,
        SecurityAutoConfiguration::class,
        ManagementWebSecurityAutoConfiguration::class,
    ],
)
@Import(TrafficWebSocketHandshakeInterceptor::class)
class TrafficStompTestApp

@Configuration
@EnableWebSocketMessageBroker
class TrafficStompTestBrokerConfig(
    private val trafficHandshakeInterceptor: TrafficWebSocketHandshakeInterceptor,
) : WebSocketMessageBrokerConfigurer {
    override fun configureMessageBroker(registry: MessageBrokerRegistry) {
        registry.enableSimpleBroker("/topic")
        registry.setApplicationDestinationPrefixes("/app")
    }
    override fun registerStompEndpoints(registry: StompEndpointRegistry) {
        registry.addEndpoint("/ws")
            .addInterceptors(trafficHandshakeInterceptor)
            .setAllowedOriginPatterns("*")
            .withSockJS()
    }
}

@Controller
class TrafficStompEchoController {
    @MessageMapping("/echo")
    @SendTo("/topic/echo")
    fun echo(body: Map<String, Any>) = body
}

@SpringBootTest(classes = [TrafficStompTestApp::class], webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = ["traffic.api-key=test-key"])
class TrafficStompConnectionTest {
    @LocalServerPort
    private var port: Int = 0

    @Test
    fun `internal socket accepts a live STOMP session with the service key`() {
        val received = CountDownLatch(1)
        val connected = connect("test-key", received)
        assertTrue(connected.await(8, TimeUnit.SECONDS))
        assertTrue(received.await(8, TimeUnit.SECONDS))
    }

    @Test
    fun `internal socket rejects a live handshake without the service key`() {
        val connected = CountDownLatch(1)
        val failed = AtomicBoolean(false)
        val client = WebSocketStompClient(StandardWebSocketClient()).apply {
            messageConverter = MappingJackson2MessageConverter()
        }
        client.connect("ws://127.0.0.1:$port/ws/websocket", WebSocketHttpHeaders(), object : StompSessionHandlerAdapter() {
            override fun afterConnected(session: StompSession, connectedHeaders: StompHeaders) {
                connected.countDown()
            }
            override fun handleTransportError(session: StompSession, exception: Throwable) {
                failed.set(true)
            }
        })
        assertFalse(connected.await(3, TimeUnit.SECONDS))
    }

    private fun connect(key: String, received: CountDownLatch): CountDownLatch {
        val connected = CountDownLatch(1)
        val client = WebSocketStompClient(StandardWebSocketClient()).apply {
            messageConverter = MappingJackson2MessageConverter()
        }
        val headers = WebSocketHttpHeaders().apply { add("X-Traffic-Key", key) }
        client.connect("ws://127.0.0.1:$port/ws/websocket", headers, object : StompSessionHandlerAdapter() {
            override fun afterConnected(session: StompSession, connectedHeaders: StompHeaders) {
                session.subscribe("/topic/echo", object : StompFrameHandler {
                    override fun getPayloadType(headers: StompHeaders): Type = Map::class.java
                    override fun handleFrame(headers: StompHeaders, payload: Any?) {
                        received.countDown()
                    }
                })
                session.send("/app/echo", mapOf("ok" to true))
                connected.countDown()
            }
        })
        return connected
    }
}
