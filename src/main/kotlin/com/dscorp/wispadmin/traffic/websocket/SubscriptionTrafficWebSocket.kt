package com.dscorp.wispadmin.traffic.websocket

import com.dscorp.wispadmin.traffic.service.SubscriptionTrafficLiveTickBuilder
import com.dscorp.wispadmin.traffic.service.SubscriptionTrafficLiveTickState
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionRepository
import com.dscorp.wispadmin.wispadmin.service.MikroTikConnectionService
import com.dscorp.wispadmin.wispadmin.websocket.WebSocketSessionCleanup
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.messaging.simp.SimpMessageHeaderAccessor
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Controller
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

@Controller
class SubscriptionTrafficWebSocket(
    @Autowired private val messagingTemplate: SimpMessagingTemplate,
    @Autowired private val subscriptionRepository: SubscriptionRepository,
    @Autowired private val mikrotikConnectionService: MikroTikConnectionService
) : WebSocketSessionCleanup {
    private val logger = LoggerFactory.getLogger(SubscriptionTrafficWebSocket::class.java)

    private data class SubscriptionMonitorTarget(
        val subscriptionId: Int,
        val ip: String,
        val deviceId: Int
    )

    private val runningTasks = mutableMapOf<Int, ScheduledFuture<*>>()
    private val activeSessions = ConcurrentHashMap<Int, MutableSet<String>>()
    private val sessionActivity = ConcurrentHashMap<String, Long>()
    private val subscriptionTargets = ConcurrentHashMap<Int, SubscriptionMonitorTarget>()
    private val deviceSubscriptions = ConcurrentHashMap<Int, MutableSet<Int>>()
    private val tickStates = ConcurrentHashMap<Int, SubscriptionTrafficLiveTickState>()
    private val cleanupScheduler: ScheduledExecutorService = Executors.newScheduledThreadPool(1)

    private val sessionTimeoutMs = 5 * 60 * 1000L
    private val monitorIntervalMs = 2000L

    init {
        cleanupScheduler.scheduleAtFixedRate({
            cleanupInactiveSessions()
        }, 1, 1, TimeUnit.MINUTES)
    }

    @MessageMapping("/subscription-traffic/start")
    fun startSubscriptionTrafficMonitor(request: Map<String, Any>, headerAccessor: SimpMessageHeaderAccessor) {
        val sessionId = headerAccessor.sessionId ?: return
        val subscriptionId = (request["subscriptionId"] as? Number)?.toInt() ?: return

        if (activeSessions[subscriptionId]?.contains(sessionId) == true) {
            sessionActivity[sessionId] = System.currentTimeMillis()
            return
        }

        val subscription = subscriptionRepository.findByIdWithHostDevice(subscriptionId) ?: run {
            logger.warn("Subscription {} not found for live traffic", subscriptionId)
            return
        }

        val ip = subscription.ip?.trim().orEmpty()
        val hostDevice = subscription.hostDevice
        if (ip.isEmpty() || hostDevice == null) {
            logger.warn("Subscription {} missing ip or hostDevice for live traffic", subscriptionId)
            return
        }

        val deviceId = hostDevice.id
        activeSessions.computeIfAbsent(subscriptionId) { mutableSetOf() }.add(sessionId)
        sessionActivity[sessionId] = System.currentTimeMillis()
        subscriptionTargets[subscriptionId] = SubscriptionMonitorTarget(
            subscriptionId = subscriptionId,
            ip = ip,
            deviceId = deviceId
        )
        deviceSubscriptions.computeIfAbsent(deviceId) { mutableSetOf() }.add(subscriptionId)

        if (deviceSubscriptions[deviceId]?.size == 1) {
            startDeviceMonitoring(hostDevice, deviceId)
        }
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
            .forEach { subscriptionId ->
                removeSession(subscriptionId, sessionId)
            }
        sessionActivity.remove(sessionId)
    }

    private fun startDeviceMonitoring(device: com.dscorp.wispadmin.wispadmin.data.model.NetworkDevice, deviceId: Int) {
        if (runningTasks.containsKey(deviceId)) return

        val task = mikrotikConnectionService.scheduleMonitoring(
            device = device,
            path = "/queue/simple",
            intervalMs = monitorIntervalMs,
            onData = { queueRows ->
                if (deviceSubscriptions[deviceId].isNullOrEmpty()) {
                    stopDeviceMonitoring(deviceId)
                    return@scheduleMonitoring
                }
                publishTicksForDevice(deviceId, queueRows)
            },
            shouldContinue = { !deviceSubscriptions[deviceId].isNullOrEmpty() }
        )
        runningTasks[deviceId] = task
    }

    private fun publishTicksForDevice(deviceId: Int, queueRows: List<Map<String, String>>) {
        val subscriptionIds = deviceSubscriptions[deviceId]?.toList().orEmpty()
        subscriptionIds.forEach { subscriptionId ->
            val target = subscriptionTargets[subscriptionId] ?: return@forEach
            val queueRow = SubscriptionTrafficLiveTickBuilder.findQueueRowForIp(queueRows, target.ip)
            val previous = tickStates[subscriptionId] ?: SubscriptionTrafficLiveTickState()
            val result = SubscriptionTrafficLiveTickBuilder.buildFromQueueRow(
                subscriptionId = subscriptionId,
                queueRow = queueRow,
                previous = previous,
                intervalSeconds = monitorIntervalMs / 1000.0
            )
            tickStates[subscriptionId] = result.nextState
            messagingTemplate.convertAndSend("/topic/subscription-traffic/$subscriptionId", result.tick)
        }
    }

    private fun removeSession(subscriptionId: Int, sessionId: String) {
        val removed = activeSessions[subscriptionId]?.remove(sessionId) ?: false
        sessionActivity.remove(sessionId)
        if (!removed) return

        if (activeSessions[subscriptionId].isNullOrEmpty()) {
            activeSessions.remove(subscriptionId)
            val target = subscriptionTargets.remove(subscriptionId)
            tickStates.remove(subscriptionId)
            target?.let { deviceTarget ->
                deviceSubscriptions[deviceTarget.deviceId]?.remove(subscriptionId)
                if (deviceSubscriptions[deviceTarget.deviceId].isNullOrEmpty()) {
                    deviceSubscriptions.remove(deviceTarget.deviceId)
                    stopDeviceMonitoring(deviceTarget.deviceId)
                }
            }
        }
    }

    private fun stopDeviceMonitoring(deviceId: Int) {
        runningTasks[deviceId]?.cancel(false)
        runningTasks.remove(deviceId)
        mikrotikConnectionService.closeConnection(deviceId)
    }

    private fun cleanupInactiveSessions() {
        val currentTime = System.currentTimeMillis()
        sessionActivity.entries
            .filter { (_, lastActivity) -> currentTime - lastActivity > sessionTimeoutMs }
            .map { it.key }
            .forEach { sessionId ->
                handleUserDisconnect(sessionId)
            }
    }
}
