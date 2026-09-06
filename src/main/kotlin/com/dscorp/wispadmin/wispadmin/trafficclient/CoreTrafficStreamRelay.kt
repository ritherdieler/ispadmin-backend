package com.dscorp.wispadmin.wispadmin.trafficclient

import com.dscorp.wispadmin.wispadmin.repository.SubscriptionRepository
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.event.EventListener
import org.springframework.context.annotation.Lazy
import org.springframework.messaging.*
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.messaging.simp.*
import org.springframework.messaging.simp.stomp.*
import org.springframework.messaging.support.ChannelInterceptor
import org.springframework.stereotype.Controller
import org.springframework.web.socket.messaging.SessionDisconnectEvent

interface TrafficStreamTransport {
    fun start(subscriptionId: Int,receive: (Any)->Unit)
    fun stop(subscriptionId: Int)
}

@Controller
@ConditionalOnProperty(prefix="traffic",name=["client-enabled"],havingValue="true")
class CoreTrafficStreamRelay(
    private val upstream: TrafficStreamTransport,
    @Lazy private val messaging: SimpMessagingTemplate,
    private val subscriptions: SubscriptionRepository,
) : ChannelInterceptor {
    private val sessions=mutableMapOf<Int,MutableSet<String>>()

    @MessageMapping("/subscription-traffic/start")
    @Synchronized fun start(request: Map<String,Any>,headers: SimpMessageHeaderAccessor) {
        authorize(headers)
        val id=(request["subscriptionId"] as? Number)?.toInt() ?: throw IllegalArgumentException("Subscription is required")
        require(subscriptions.existsById(id)) { "Subscription not found" }
        val session=requireNotNull(headers.sessionId)
        val watchers=sessions.getOrPut(id) { mutableSetOf() }
        if(watchers.add(session) && watchers.size==1) upstream.start(id) { payload -> messaging.convertAndSend("/topic/subscription-traffic/$id",payload) }
    }
    @MessageMapping("/subscription-traffic/stop")
    @Synchronized fun stop(request: Map<String,Any>,headers: SimpMessageHeaderAccessor) {
        authorize(headers)
        val id=(request["subscriptionId"] as? Number)?.toInt() ?: return
        remove(id,requireNotNull(headers.sessionId))
    }
    @EventListener fun onDisconnect(event: SessionDisconnectEvent)=disconnect(event.sessionId)
    @Synchronized fun disconnect(session: String) { sessions.keys.toList().forEach { remove(it,session) } }
    private fun remove(id: Int,session: String) {
        val watchers=sessions[id] ?: return
        watchers.remove(session)
        if(watchers.isEmpty()) { sessions.remove(id);upstream.stop(id) }
    }
    override fun preSend(message: Message<*>,channel: MessageChannel): Message<*> {
        val headers=StompHeaderAccessor.wrap(message)
        val destination=headers.destination.orEmpty()
        if(destination.startsWith("/topic/subscription-traffic/")) {
            require(headers.command==StompCommand.SUBSCRIBE) { "Clients cannot publish traffic samples" }
            authorize(headers)
            val id=destination.removePrefix("/topic/subscription-traffic/").toIntOrNull()
            require(id!=null && subscriptions.existsById(id)) { "Subscription not found" }
        }
        if(destination.startsWith("/app/subscription-traffic/")) authorize(headers)
        return message
    }
    private fun authorize(headers: SimpMessageHeaderAccessor) {
        require(headers.sessionAttributes?.get("authUserId") is Number && headers.sessionAttributes?.get("authUserType") in setOf("ADMIN","TECHNICIAN")) { "Technical session required" }
    }
}
