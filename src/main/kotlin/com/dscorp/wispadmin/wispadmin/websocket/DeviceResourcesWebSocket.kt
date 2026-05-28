package com.dscorp.wispadmin.wispadmin.websocket

import com.dscorp.wispadmin.wispadmin.data.model.NetworkDevice
import com.dscorp.wispadmin.wispadmin.repository.NetworkDeviceRepository
import com.dscorp.wispadmin.wispadmin.service.MikroTikConnectionService
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

/**
 * Monitoreo en tiempo real de Recursos del dispositivo (CPU, Memoria, Disco, Uptime, etc.)
 * Sigue la misma estrategia de start/stop, sesiones activas y limpieza automática
 * usada por InterfaceTrafficWebSocket.
 */
@Controller
class DeviceResourcesWebSocket(
    @Autowired private val messagingTemplate: SimpMessagingTemplate,
    @Autowired private val networkDeviceRepository: NetworkDeviceRepository,
    @Autowired private val mikrotikConnectionService: MikroTikConnectionService
) {
    private val logger = LoggerFactory.getLogger(DeviceResourcesWebSocket::class.java)

    // Tareas programadas por dispositivo
    private val runningTasks = mutableMapOf<Int, ScheduledFuture<*>>()

    // Sesiones activas por dispositivo
    private val activeSessions = ConcurrentHashMap<Int, MutableSet<String>>()

    // Última actividad por sesión
    private val sessionActivity = ConcurrentHashMap<String, Long>()

    // Scheduler para limpieza automática
    private val cleanupScheduler: ScheduledExecutorService = Executors.newScheduledThreadPool(1)

    // Timeout para sesiones inactivas (5 minutos)
    private val SESSION_TIMEOUT_MS = 5 * 60 * 1000L

    init {
        logger.info("🚀 Inicializando DeviceResourcesWebSocket - Monitoreo de recursos")
        logger.info("🔍 [DEBUG] Endpoints WebSocket disponibles:")
        logger.info("   - /app/resources/start")
        logger.info("   - /app/resources/stop")

        // Limpieza automática cada minuto
        cleanupScheduler.scheduleAtFixedRate({
            cleanupInactiveSessions()
        }, 1, 1, TimeUnit.MINUTES)
        logger.info("✅ Limpieza automática programada cada minuto")
    }

    @MessageMapping("/resources/start")
    fun startResourcesMonitor(request: Map<String, Any>, headerAccessor: SimpMessageHeaderAccessor) {
        logger.info("🔍 [DEBUG][RES] Mensaje recibido en /resources/start: $request")
        val sessionId = headerAccessor.sessionId ?: run {
            logger.warn("❌ [RES] Intento de iniciar monitoreo sin sessionId")
            return
        }

        val deviceId = request["deviceId"] as? Int ?: run {
            logger.error("❌ [RES][SESION-$sessionId] deviceId no válido en la solicitud: $request")
            return
        }

        logger.info("📡 [RES][SESION-$sessionId] Inicio monitoreo recursos para dispositivo $deviceId")

        if (activeSessions[deviceId]?.contains(sessionId) == true) {
            logger.info("ℹ️ [RES][SESION-$sessionId] Ya monitoreando dispositivo $deviceId")
            return
        }

        val device = networkDeviceRepository.findById(deviceId).orElse(null) ?: run {
            logger.error("❌ [RES][SESION-$sessionId] Dispositivo $deviceId no encontrado")
            return
        }

        // Registrar sesión activa
        activeSessions.computeIfAbsent(deviceId) { mutableSetOf() }.add(sessionId)
        sessionActivity[sessionId] = System.currentTimeMillis()
        logger.info("📊 [RES][SESION-$sessionId] Sesión registrada para dispositivo $deviceId - Total sesiones: ${activeSessions[deviceId]?.size}")

        // Programar tarea si es la primera sesión del dispositivo
        if (activeSessions[device.id]?.size == 1) {
            val task = mikrotikConnectionService.scheduleMonitoring(
                device = device,
                command = "/system/resource/print",
                intervalMs = 1000L, // 1 segundo
                onData = { systemResource ->
                    if (activeSessions[device.id]?.isNotEmpty() == true) {
                        try {
                            val systemInfo = systemResource.firstOrNull() ?: emptyMap()

                            val totalMemory = (systemInfo["total-memory"] ?: "0").toString().toLongOrNull() ?: 0L
                            val freeMemory = (systemInfo["free-memory"] ?: "0").toString().toLongOrNull() ?: 0L
                            val usedMemory = totalMemory - freeMemory
                            val memPct = if (totalMemory > 0) (usedMemory * 100 / totalMemory) else 0

                            val totalHdd = (systemInfo["total-hdd-space"] ?: "0").toString().toLongOrNull() ?: 0L
                            val freeHdd = (systemInfo["free-hdd-space"] ?: "0").toString().toLongOrNull() ?: 0L
                            val usedHdd = totalHdd - freeHdd
                            val diskPct = if (totalHdd > 0) (usedHdd * 100 / totalHdd) else 0

                            val resourcesPayload = mapOf(
                                "cpu" to mapOf(
                                    "load" to (systemInfo["cpu-load"] ?: "0").toString(),
                                    "count" to (systemInfo["cpu-count"] ?: "1").toString(),
                                    "frequency" to (systemInfo["cpu-frequency"] ?: "0").toString()
                                ),
                                "memory" to mapOf(
                                    "total" to totalMemory,
                                    "free" to freeMemory,
                                    "used" to usedMemory,
                                    "percentage" to memPct
                                ),
                                "disk" to mapOf(
                                    "total" to totalHdd,
                                    "free" to freeHdd,
                                    "used" to usedHdd,
                                    "percentage" to diskPct
                                ),
                                "uptime" to (systemInfo["uptime"] ?: "0").toString(),
                                "version" to (systemInfo["version"] ?: "").toString(),
                                "boardName" to (systemInfo["board-name"] ?: "").toString(),
                                "architecture" to (systemInfo["architecture-name"] ?: "").toString()
                            )

                            messagingTemplate.convertAndSend("/topic/resources/${device.id}", resourcesPayload)
                            logger.debug("📡 [RES][DISPOSITIVO-${device.id}] Datos enviados - ${activeSessions[device.id]?.size} sesiones activas")
                        } catch (e: Exception) {
                            logger.error("❌ [RES][DISPOSITIVO-${device.id}] Error preparando datos de recursos: ${e.message}")
                        }
                    } else {
                        logger.info("⏹️ [RES][DISPOSITIVO-${device.id}] Sin sesiones activas, deteniendo envío de datos")
                        runningTasks[device.id]?.cancel(false)
                        runningTasks.remove(device.id)
                        mikrotikConnectionService.closeConnection(device.id)
                        activeSessions.remove(device.id)
                    }
                },
                shouldContinue = { activeSessions[device.id]?.isNotEmpty() == true }
            )

            runningTasks[device.id] = task
            logger.info("✅ [RES][DISPOSITIVO-${device.id}] Monitoreo de recursos iniciado - ${activeSessions[device.id]?.size} sesiones activas")
        } else {
            logger.info("➕ [RES][SESION-$sessionId] Uniéndose a monitoreo existente de recursos para $deviceId (${activeSessions[deviceId]?.size} sesiones)")
        }

        logCurrentStats()
    }

    @MessageMapping("/resources/stop")
    fun stopResourcesMonitor(request: Map<String, Any>, headerAccessor: SimpMessageHeaderAccessor) {
        logger.info("🔍 [DEBUG][RES] Mensaje recibido en /resources/stop: $request")
        val sessionId = headerAccessor.sessionId ?: run {
            logger.warn("❌ [RES] Intento de detener monitoreo sin sessionId")
            return
        }

        val deviceId = request["deviceId"] as? Int ?: run {
            logger.error("❌ [RES][SESION-$sessionId] deviceId no válido en la solicitud: $request")
            return
        }

        logger.info("🛑 [RES][SESION-$sessionId] Deteniendo monitoreo de recursos para $deviceId")

        val wasRemoved = activeSessions[deviceId]?.remove(sessionId) ?: false
        sessionActivity.remove(sessionId)
        if (!wasRemoved) {
            logger.warn("⚠️ [RES][SESION-$sessionId] Sesión no encontrada para dispositivo $deviceId")
            return
        }

        if (activeSessions[deviceId]?.isEmpty() != false) {
            logger.info("🔌 [RES][SESION-$sessionId] Última sesión para $deviceId - Cerrando conexión MikroTik")
            runningTasks[deviceId]?.cancel(false)
            runningTasks.remove(deviceId)
            mikrotikConnectionService.closeConnection(deviceId)
            activeSessions.remove(deviceId)
            logger.info("✅ [RES][SESION-$sessionId] Monitoreo de recursos detenido para $deviceId")
        } else {
            logger.info("➖ [RES][SESION-$sessionId] Sesión removida - ${activeSessions[deviceId]?.size} sesiones restantes para $deviceId")
        }

        logCurrentStats()
    }

    fun handleUserDisconnect(sessionId: String) {
        logger.info("🔌 [RES][SESION-$sessionId] Usuario desconectado - Limpieza de recursos")

        val devicesToCheck = activeSessions.entries
            .filter { (_, sessions) -> sessions.contains(sessionId) }
            .map { it.key }

        devicesToCheck.forEach { deviceId ->
            val tempHeaderAccessor = SimpMessageHeaderAccessor.create()
            tempHeaderAccessor.sessionId = sessionId
            stopResourcesMonitor(mapOf("deviceId" to deviceId), tempHeaderAccessor)
        }

        sessionActivity.remove(sessionId)
        logCurrentStats()
    }

    private fun cleanupInactiveSessions() {
        val currentTime = System.currentTimeMillis()
        val inactiveSessions = sessionActivity.entries
            .filter { (_, lastActivity) -> currentTime - lastActivity > SESSION_TIMEOUT_MS }
            .map { it.key }

        if (inactiveSessions.isNotEmpty()) {
            logger.info("🧹 [RES] Limpieza automática - ${inactiveSessions.size} sesiones inactivas")
            inactiveSessions.forEach { sessionId -> handleUserDisconnect(sessionId) }
        }
    }

    private fun logCurrentStats() {
        val stats = mapOf(
            "activeDevices" to activeSessions.size,
            "totalSessions" to sessionActivity.size,
            "runningTasks" to runningTasks.size,
            "deviceSessions" to activeSessions.mapValues { it.value.size }
        )
        logger.info("📈 [RES] Estado actual: $stats")
    }
}


