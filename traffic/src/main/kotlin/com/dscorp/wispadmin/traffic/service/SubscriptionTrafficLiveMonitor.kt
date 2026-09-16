package com.dscorp.wispadmin.traffic.service

import com.dscorp.wispadmin.routeros.config.RouterOsClientProperties
import com.dscorp.wispadmin.routeros.port.MikrotikClient
import com.dscorp.wispadmin.routeros.port.MikrotikDeviceRef
import com.dscorp.wispadmin.traffic.entity.TrafficRouter
import com.dscorp.wispadmin.traffic.port.TrafficDirectoryPort
import com.dscorp.wispadmin.traffic.repository.TrafficRouterRepository
import com.dscorp.wispadmin.transport.LiveTrafficStreamPort
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import javax.annotation.PreDestroy

@Component
class SubscriptionTrafficLiveMonitor(
    private val directory: TrafficDirectoryPort,
    private val routerRepository: TrafficRouterRepository,
    @Qualifier("trafficPollMikrotikClient")
    private val mikrotikClient: MikrotikClient,
    private val routerOsClientProperties: RouterOsClientProperties,
) : LiveTrafficStreamPort {
    private val logger = LoggerFactory.getLogger(javaClass)
    private data class Target(
        val subscriptionId: Int,
        val ip: String,
        val pppoeUsername: String?,
        val deviceId: Int,
    )

    private val receivers = ConcurrentHashMap<Int, (Any) -> Unit>()
    private val targets = ConcurrentHashMap<Int, Target>()
    private val deviceSubscriptions = ConcurrentHashMap<Int, MutableSet<Int>>()
    private val tickStates = ConcurrentHashMap<Int, SubscriptionTrafficLiveTickState>()
    private val runningTasks = ConcurrentHashMap<Int, ScheduledFuture<*>>()
    private val scheduler: ScheduledExecutorService = Executors.newScheduledThreadPool(2)
    private val monitorIntervalMs = 2000L

    @Synchronized
    override fun start(command: Map<String, Any>, receive: (Any) -> Unit) {
        val subscriptionId = (command["subscriptionId"] as? Number)?.toInt() ?: return
        val directoryTarget = try {
            directory.list().firstOrNull { it.subscriptionId == subscriptionId }
        } catch (ex: Exception) {
            logger.warn("Live traffic directory failed for {}: {}", subscriptionId, ex.message)
            null
        }
        val resolved = SubscriptionLiveMonitorTarget.resolve(subscriptionId, command, directoryTarget) ?: run {
            logger.warn("Subscription {} missing ip and pppoe username for live traffic", subscriptionId)
            return
        }
        val deviceId = resolved.routerHint ?: routerRepository.findByEnabledTrue().firstOrNull()?.id ?: return
        receivers[subscriptionId] = receive
        targets[subscriptionId] = Target(subscriptionId, resolved.ip, resolved.pppoeUsername, deviceId)
        val watchers = deviceSubscriptions.computeIfAbsent(deviceId) { mutableSetOf() }
        if (watchers.add(subscriptionId) && watchers.size == 1) {
            val router = routerRepository.findById(deviceId).orElse(null) ?: return
            startDeviceMonitoring(router, deviceId)
        }
    }

    @Synchronized
    override fun stop(subscriptionId: Int) {
        receivers.remove(subscriptionId)
        tickStates.remove(subscriptionId)
        val target = targets.remove(subscriptionId) ?: return
        deviceSubscriptions[target.deviceId]?.remove(subscriptionId)
        if (deviceSubscriptions[target.deviceId].isNullOrEmpty()) {
            deviceSubscriptions.remove(target.deviceId)
            stopDeviceMonitoring(target.deviceId)
        }
    }

    private fun startDeviceMonitoring(router: TrafficRouter, deviceId: Int) {
        if (runningTasks.containsKey(deviceId)) return
        val deviceRef = MikrotikDeviceRef(
            id = router.id.toString(),
            host = router.host,
            port = routerOsClientProperties.classic.port,
            username = router.username,
            password = router.password,
        )
        runningTasks[deviceId] = scheduler.scheduleAtFixedRate({
            if (deviceSubscriptions[deviceId].isNullOrEmpty()) {
                stopDeviceMonitoring(deviceId)
                return@scheduleAtFixedRate
            }
            try {
                mikrotikClient.withSession(deviceRef) { session ->
                    val queues = session.print("/queue/simple", proplist = listOf(".id", "target", "name", "bytes", "rate"))
                    publishTicksForDevice(deviceId, queues)
                }
            } catch (ex: Exception) {
                logger.warn("Live traffic poll failed for router {}: {}", deviceId, ex.message)
            }
        }, 0, monitorIntervalMs, TimeUnit.MILLISECONDS)
    }

    private fun publishTicksForDevice(deviceId: Int, queueRows: List<Map<String, String>>) {
        val subscriptionIds = deviceSubscriptions[deviceId]?.toList().orEmpty()
        subscriptionIds.forEach { subscriptionId ->
            val target = targets[subscriptionId] ?: return@forEach
            val queueRow = SubscriptionTrafficLiveTickBuilder.findQueueRow(
                queueRows,
                target.ip,
                target.pppoeUsername,
            )
            val previous = tickStates[subscriptionId] ?: SubscriptionTrafficLiveTickState()
            val result = SubscriptionTrafficLiveTickBuilder.buildFromQueueRow(
                subscriptionId = subscriptionId,
                queueRow = queueRow,
                previous = previous,
                intervalSeconds = monitorIntervalMs / 1000.0,
            )
            tickStates[subscriptionId] = result.nextState
            receivers[subscriptionId]?.invoke(result.tick)
        }
    }

    private fun stopDeviceMonitoring(deviceId: Int) {
        runningTasks.remove(deviceId)?.cancel(false)
    }

    @PreDestroy
    fun close() {
        runningTasks.keys.toList().forEach { stopDeviceMonitoring(it) }
        receivers.clear()
        targets.clear()
        deviceSubscriptions.clear()
        tickStates.clear()
        scheduler.shutdownNow()
    }
}
