package com.dscorp.wispadmin.wispadmin.trafficclient

import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.messaging.converter.MappingJackson2MessageConverter
import org.springframework.messaging.simp.stomp.*
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import org.springframework.stereotype.Component
import org.springframework.web.socket.WebSocketHttpHeaders
import org.springframework.web.socket.client.standard.StandardWebSocketClient
import org.springframework.web.socket.messaging.WebSocketStompClient
import java.lang.reflect.Type
import javax.annotation.PreDestroy

@Component
@ConditionalOnProperty(prefix="traffic",name=["client-enabled"],havingValue="true")
class StompTrafficStreamTransport(private val properties: TrafficClientProperties) : TrafficStreamTransport {
    private val log=LoggerFactory.getLogger(javaClass)
    private val scheduler=ThreadPoolTaskScheduler().apply { poolSize=1;setThreadNamePrefix("traffic-heartbeat-");initialize() }
    private val client=WebSocketStompClient(StandardWebSocketClient()).apply {
        messageConverter=MappingJackson2MessageConverter()
        taskScheduler=scheduler
        defaultHeartbeat=longArrayOf(10000,10000)
    }
    private val receivers=mutableMapOf<Int,(Any)->Unit>()
    private val bindings=mutableMapOf<Int,StompSession.Subscription>()
    private var session: StompSession?=null
    private var connecting=false

    @Synchronized override fun start(subscriptionId: Int,receive: (Any)->Unit) {
        receivers[subscriptionId]=receive
        session?.takeIf { it.isConnected }?.let { bind(it,subscriptionId) }
        reconnect()
    }
    @Synchronized override fun stop(subscriptionId: Int) {
        receivers.remove(subscriptionId)
        runCatching { bindings.remove(subscriptionId)?.unsubscribe() }
        runCatching { session?.takeIf { it.isConnected }?.send("/app/subscription-traffic/stop",mapOf("subscriptionId" to subscriptionId)) }
    }
    @Scheduled(fixedDelay=5000)
    @Synchronized fun reconnect() {
        if(receivers.isEmpty() || connecting) return
        session?.takeIf { it.isConnected }?.let { connected ->
            receivers.keys.forEach { id ->
                runCatching { connected.send("/app/subscription-traffic/start",mapOf("subscriptionId" to id)) }
                    .onFailure { session=null;bindings.clear() }
            }
            return
        }
        val base=properties.internalBaseUrl.trim().trimEnd('/')
        if(base.isBlank()) return
        connecting=true
        val headers=WebSocketHttpHeaders().apply { set("X-Traffic-Key",properties.apiKey) }
        val url=base.replaceFirst(Regex("^http"),"ws")+"/ws/websocket"
        try {
            client.connect(url,headers,object: StompSessionHandlerAdapter() {
                override fun afterConnected(connected: StompSession,headers: StompHeaders) {
                    synchronized(this@StompTrafficStreamTransport) {
                        session=connected;connecting=false;bindings.clear()
                        receivers.keys.toList().forEach { bind(connected,it) }
                    }
                }
                override fun handleTransportError(failed: StompSession,error: Throwable) {
                    synchronized(this@StompTrafficStreamTransport) {
                        if(session===failed) { session=null;bindings.clear() }
                        connecting=false
                    }
                }
            }).addCallback({}, {
                synchronized(this) { connecting=false }
                log.warn("Internal traffic stream connection failed")
            })
        } catch(ex: Exception) { connecting=false;log.warn("Internal traffic stream connection failed") }
    }
    private fun bind(connected: StompSession,id: Int) {
        if(bindings.containsKey(id)) return
        bindings[id]=connected.subscribe("/topic/subscription-traffic/$id",object: StompFrameHandler {
            override fun getPayloadType(headers: StompHeaders): Type = Map::class.java
            override fun handleFrame(headers: StompHeaders,payload: Any?) {
                val receiver=synchronized(this@StompTrafficStreamTransport) { receivers[id] }
                if(payload!=null) receiver?.invoke(payload)
            }
        })
        connected.send("/app/subscription-traffic/start",mapOf("subscriptionId" to id))
    }
    @PreDestroy @Synchronized fun close() {
        receivers.clear();bindings.clear()
        runCatching { session?.disconnect() };session=null
        client.stop();scheduler.shutdown()
    }
}
