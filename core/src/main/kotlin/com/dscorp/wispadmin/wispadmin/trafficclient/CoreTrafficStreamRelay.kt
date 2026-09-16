package com.dscorp.wispadmin.wispadmin.trafficclient

import com.dscorp.wispadmin.transport.LiveTrafficStreamPort
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

typealias TrafficStreamTransport = LiveTrafficStreamPort

@Controller
@ConditionalOnProperty(prefix="traffic",name=["client-enabled"],havingValue="true")
class CoreTrafficStreamRelay(
    private val upstream: LiveTrafficStreamPort,
    @Lazy private val messaging: SimpMessagingTemplate,
    private val subscriptions: SubscriptionRepository,
    private val directory: TrafficDirectoryService,
) : ChannelInterceptor {
    private val sessions=mutableMapOf<Int,MutableSet<String>>()

    @MessageMapping("/subscription-traffic/start")
    @Synchronized fun start(request: Map<String,Any>,headers: SimpMessageHeaderAccessor) {
        authorize(headers)
        val id=(request["subscriptionId"] as? Number)?.toInt() ?: throw IllegalArgumentException("Subscription is required")
        require(subscriptions.existsById(id)) { "Subscription not found" }
        val session=requireNotNull(headers.sessionId)
        val watchers=sessions.getOrPut(id) { mutableSetOf() }
        if(watchers.add(session) && watchers.size==1) {
            upstream.start(liveStartCommand(id)) { payload -> messaging.convertAndSend("/topic/subscription-traffic/$id",payload) }
        }
    }

    private fun liveStartCommand(id: Int): Map<String, Any> {
        val command = mutableMapOf<String, Any>("subscriptionId" to id)
        val entry = directory.list().firstOrNull { it.subscriptionId == id } ?: return command
        val ip = entry.ip.trim().takeIf { it.isNotEmpty() }
        val pppoe = entry.pppoeUsername?.trim()?.takeIf { it.isNotEmpty() }
        check(ip == null || pppoe == null) { "Traffic directory emitted ip and pppoeUsername for $id" }
        ip?.let { command["ip"] = it }
        pppoe?.let { command["pppoeUsername"] = it }
        entry.routerHint?.let { command["routerHint"] = it }
        return command
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
