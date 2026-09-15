package com.dscorp.wispadmin.traffic.websocket

import com.dscorp.wispadmin.traffic.dto.SubscriptionTrafficLiveTickDto
import com.dscorp.wispadmin.traffic.service.SubscriptionTrafficLiveMonitor
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.messaging.simp.SimpMessageHeaderAccessor
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Controller
import java.util.concurrent.ConcurrentHashMap

@Controller
@ConditionalOnProperty(prefix = "traffic", name = ["client-enabled"], havingValue = "false", matchIfMissing = true)
class SubscriptionTrafficWebSocket(
    private val messagingTemplate: SimpMessagingTemplate,
    private val monitor: SubscriptionTrafficLiveMonitor,
) : TrafficWebSocketSessionCleanup {
    private val activeSessions = ConcurrentHashMap<Int, MutableSet<String>>()
    private val sessionActivity = ConcurrentHashMap<String, Long>()

    @MessageMapping("/subscription-traffic/start")
    fun startSubscriptionTrafficMonitor(request: Map<String, Any>, headerAccessor: SimpMessageHeaderAccessor) {
        val sessionId = headerAccessor.sessionId ?: return
        val subscriptionId = (request["subscriptionId"] as? Number)?.toInt() ?: return
        if (activeSessions[subscriptionId]?.contains(sessionId) == true) {
            sessionActivity[sessionId] = System.currentTimeMillis()
            return
        }
        val watchers = activeSessions.computeIfAbsent(subscriptionId) { mutableSetOf() }
        if (watchers.add(sessionId) && watchers.size == 1) {
            monitor.start(request) { payload ->
                val id = (payload as? SubscriptionTrafficLiveTickDto)?.subscriptionId ?: subscriptionId
                messagingTemplate.convertAndSend("/topic/subscription-traffic/$id", payload)
            }
        }
        sessionActivity[sessionId] = System.currentTimeMillis()
    }

    @MessageMapping("/subscription-traffic/stop")
    fun stopSubscriptionTrafficMonitor(request: Map<String, Any>, headerAccessor: SimpMessageHeaderAccessor) {
        val sessionId = headerAccessor.sessionId ?: return
        val subscriptionId = (request["subscriptionId"] as? Number)?.toInt() ?: return
        removeSession(subscriptionId, sessionId)
    }

    override fun handleUserDisconnect(sessionId: String) {
        activeSessions.entries
            .filter { (_, sessions) -> sessions.contains(sessionId) }
            .map { it.key }
            .forEach { subscriptionId -> removeSession(subscriptionId, sessionId) }
        sessionActivity.remove(sessionId)
    }

    private fun removeSession(subscriptionId: Int, sessionId: String) {
        val removed = activeSessions[subscriptionId]?.remove(sessionId) ?: false
        sessionActivity.remove(sessionId)
        if (!removed) return
        if (activeSessions[subscriptionId].isNullOrEmpty()) {
            activeSessions.remove(subscriptionId)
            monitor.stop(subscriptionId)
        }
    }
}
