package com.dscorp.wispadmin.wispadmin.websocket

import com.dscorp.wispadmin.wispadmin.data.model.NetworkDevice
import com.dscorp.wispadmin.wispadmin.repository.NetworkDeviceRepository
import com.dscorp.wispadmin.wispadmin.service.MikroTikConnectionService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.messaging.simp.SimpMessageHeaderAccessor
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.messaging.simp.annotation.SubscribeMapping
import org.springframework.stereotype.Controller
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService

/**
 * Gestión de Conexiones Persistentes para Monitoreo de Tráfico MikroTik
 * ---------------------------------------------------------------
 * Este controlador WebSocket permite monitorear el tráfico de interfaces de dispositivos MikroTik en tiempo real.
 * 
 * Arquitectura y Comportamiento:
 *
 * - Cada vez que un usuario (o pestaña) solicita monitoreo de tráfico, se registra una sesión WebSocket única.
 * - Si es la primera sesión para un dispositivo, se abre una conexión persistente a MikroTik y se inicia el monitoreo.
 * - Si ya hay monitoreo activo, la nueva sesión solo se suma al grupo de sesiones activas para ese dispositivo.
 * - Cuando una sesión deja de monitorear (cierra pestaña, navega fuera, o se desconecta), se elimina del grupo.
 * - Si no quedan sesiones activas para ese dispositivo, se cancela el monitoreo y se cierra la conexión MikroTik.
 *
 * Prevención de Fugas y Limpieza:
 *
 * - Un listener detecta desconexiones de WebSocket y ejecuta la limpieza de sesiones y recursos.
 * - Un proceso automático revisa cada minuto y elimina sesiones inactivas (timeout configurable, por defecto 5 minutos).
 * - Esto previene fugas de recursos por cierres inesperados de navegador o caídas de red.
 *
 * Escenarios:
 *
 * - Si abres/cierra la página muchas veces, solo se mantiene una conexión persistente mientras haya al menos una sesión activa.
 * - Si todas las pestañas se cierran, la conexión se cierra automáticamente.
 * - Si el usuario pierde conexión o cierra abruptamente, la sesión se limpia tras el timeout.
 *
 * Endpoint de monitoreo:
 *   GET /networkDevice/connection-stats
 *   Devuelve estadísticas de conexiones activas, sesiones y tareas de monitoreo.
 *
 * Ventajas:
 * - Eficiencia y escalabilidad.
 * - Sin fugas de recursos.
 * - Limpieza automática y robusta ante cualquier escenario de uso.
 *
 * Para futuras implementaciones:
 * - Usar este patrón para cualquier monitoreo en tiempo real que requiera conexiones persistentes.
 * - Siempre limpiar recursos al finalizar la última sesión.
 * - Implementar listeners y timeouts para evitar fugas.
 */

@Controller
class InterfaceTrafficWebSocket(
    @Autowired private val messagingTemplate: SimpMessagingTemplate,
    @Autowired private val networkDeviceRepository: NetworkDeviceRepository,
    @Autowired private val mikrotikConnectionService: MikroTikConnectionService
) : WebSocketSessionCleanup {
    private val logger = LoggerFactory.getLogger(InterfaceTrafficWebSocket::class.java)
    
    // Mapa para controlar las tareas programadas por dispositivo
    private val runningTasks = mutableMapOf<Int, ScheduledFuture<*>>()
    
    // Mapa para tracking de sesiones activas por dispositivo
    private val activeSessions = ConcurrentHashMap<Int, MutableSet<String>>()
    
    // Mapa para tracking de última actividad por sesión
    private val sessionActivity = ConcurrentHashMap<String, Long>()
    
    // Scheduler para limpieza automática
    private val cleanupScheduler: ScheduledExecutorService = Executors.newScheduledThreadPool(1)
    
    // Timeout para sesiones inactivas (5 minutos)
    private val SESSION_TIMEOUT_MS = 5 * 60 * 1000L
    
    // Mapa para guardar la última muestra de la interfaz LAN por dispositivo
    private val lanLastSample = ConcurrentHashMap<Int, Triple<Long, Long, Long>>() // deviceId -> (rxBytes, txBytes, timestamp)
    
    init {
        logger.info("🚀 Inicializando InterfaceTrafficWebSocket - Sistema de monitoreo de tráfico")
        logger.info("🔍 [DEBUG] Controlador WebSocket registrado - Endpoints disponibles:")
        logger.info("   - /app/traffic/start")
        logger.info("   - /app/traffic/stop")
        // Programar limpieza automática cada minuto
        cleanupScheduler.scheduleAtFixedRate({
            cleanupInactiveSessions()
        }, 1, 1, TimeUnit.MINUTES)
        logger.info("✅ Limpieza automática programada cada minuto")
    }

    @MessageMapping("/test")
    fun testEndpoint(message: String, headerAccessor: SimpMessageHeaderAccessor) {
        val sessionId = headerAccessor.sessionId
        logger.info("🧪 [TEST] Mensaje de prueba recibido: $message - SessionId: $sessionId")
        
        // Enviar respuesta de prueba
        messagingTemplate.convertAndSend("/topic/test", "Respuesta de prueba del servidor")
    }

    @MessageMapping("/traffic/start")
    fun startTrafficMonitor(request: Map<String, Any>, headerAccessor: SimpMessageHeaderAccessor) {
        logger.info("🔍 [DEBUG] Mensaje recibido en /traffic/start: $request")
        logger.info("🔍 [DEBUG] Headers: ${headerAccessor.sessionAttributes}")
        
        val sessionId = headerAccessor.sessionId ?: run {
            logger.warn("❌ Intento de iniciar monitoreo sin sessionId")
            return
        }
        
        val deviceId = request["deviceId"] as? Int ?: run {
            logger.error("❌ [SESION-$sessionId] deviceId no válido en la solicitud: $request")
            return
        }
        
        logger.info("📡 [SESION-$sessionId] Solicitando inicio de monitoreo para dispositivo $deviceId")
        
        // Verificar si ya hay una sesión activa para este dispositivo
        if (activeSessions[deviceId]?.contains(sessionId) == true) {
            logger.info("ℹ️ [SESION-$sessionId] Ya está monitoreando dispositivo $deviceId")
            return
        }
        
        val device = networkDeviceRepository.findById(deviceId).orElse(null) ?: run {
            logger.error("❌ [SESION-$sessionId] Dispositivo $deviceId no encontrado")
            return
        }
        
        // Registrar sesión activa primero
        activeSessions.computeIfAbsent(deviceId) { mutableSetOf() }.add(sessionId)
        sessionActivity[sessionId] = System.currentTimeMillis()
        
        logger.info("📊 [SESION-$sessionId] Sesión registrada para dispositivo $deviceId - Total sesiones: ${activeSessions[deviceId]?.size}")
        
        // Solo programar la tarea si es la primera sesión para este dispositivo
        if (activeSessions[device.id]?.size == 1) {
            val task = mikrotikConnectionService.scheduleMonitoring(
                device = device,
                path = "/interface",
                intervalMs = 1000L,
                onData = { interfaceData ->
                    // Verificar si aún hay sesiones activas para este dispositivo
                    if (activeSessions[device.id]?.isNotEmpty() == true) {
                        val traffic = interfaceData
                            .filter { it["type"] != "pppoe-in" }
                            .map { interfaceInfo ->
                                mapOf(
                                    "name" to (interfaceInfo["name"] ?: ""),
                                    "type" to (interfaceInfo["type"] ?: ""),
                                    "rxBytes" to (interfaceInfo["rx-byte"] ?: "0"),
                                    "txBytes" to (interfaceInfo["tx-byte"] ?: "0"),
                                    "rxPackets" to (interfaceInfo["rx-packet"] ?: "0"),
                                    "txPackets" to (interfaceInfo["tx-packet"] ?: "0"),
                                    "running" to (interfaceInfo["running"] ?: ""),
                                    "disabled" to (interfaceInfo["disabled"] ?: "")
                                )
                            }
                        
                        // --- INICIO BLOQUE LOG LAN ---
                        val lan = interfaceData.find { (it["name"] ?: "").toString().equals("lan", ignoreCase = true) }
                        if (lan != null) {
                            val rxBytes = (lan["rx-byte"] ?: "0").toString().toLongOrNull() ?: 0L
                            val txBytes = (lan["tx-byte"] ?: "0").toString().toLongOrNull() ?: 0L
                            val now = System.currentTimeMillis()
                            val prev = lanLastSample[device.id]
                            if (prev != null) {
                                val timeDiff = (now - prev.third) / 1000.0 // segundos
                                if (timeDiff > 0) {
                                    val rxMbps = ((rxBytes - prev.first) * 8) / (1_000_000.0 * timeDiff)
                                    val txMbps = ((txBytes - prev.second) * 8) / (1_000_000.0 * timeDiff)
                                    logger.info("[WS][Tráfico][LAN][${device.id}] Rx: %.2f Mbps | Tx: %.2f Mbps | %s".format(rxMbps, txMbps, java.time.LocalTime.now()))
                                }
                            }
                            lanLastSample[device.id] = Triple(rxBytes, txBytes, now)
                        }
                        // --- FIN BLOQUE LOG LAN ---
                        
                        messagingTemplate.convertAndSend("/topic/traffic/${device.id}", traffic)
                        logger.debug("📡 [DISPOSITIVO-${device.id}] Datos enviados - ${activeSessions[device.id]?.size} sesiones activas")
                    } else {
                        logger.info("⏹️ [DISPOSITIVO-${device.id}] No hay sesiones activas, deteniendo envío de datos")
                        // Detener la tarea programada
                        runningTasks[device.id]?.cancel(false)
                        runningTasks.remove(device.id)
                        mikrotikConnectionService.closeConnection(device.id)
                        activeSessions.remove(device.id)
                        
                        // Limpiar última muestra LAN
                        lanLastSample.remove(device.id)
                        logger.info("🧹 [DISPOSITIVO-${device.id}] Limpiando última muestra LAN por no tener sesiones activas")
                    }
                },
                shouldContinue = { activeSessions[device.id]?.isNotEmpty() == true }
            )
            
            runningTasks[device.id] = task
            logger.info("✅ [DISPOSITIVO-${device.id}] Monitoreo iniciado - ${activeSessions[device.id]?.size} sesiones activas")
        } else {
            logger.info("➕ [SESION-$sessionId] Uniéndose a monitoreo existente del dispositivo $deviceId (${activeSessions[deviceId]?.size} sesiones activas)")
        }
        
        logCurrentStats()
    }

    @MessageMapping("/traffic/stop")
    fun stopTrafficMonitor(request: Map<String, Any>, headerAccessor: SimpMessageHeaderAccessor) {
        logger.info("🔍 [DEBUG] Mensaje recibido en /traffic/stop: $request")
        logger.info("🔍 [DEBUG] Headers: ${headerAccessor.sessionAttributes}")
        
        val sessionId = headerAccessor.sessionId ?: run {
            logger.warn("❌ Intento de detener monitoreo sin sessionId")
            return
        }
        
        val deviceId = request["deviceId"] as? Int ?: run {
            logger.error("❌ [SESION-$sessionId] deviceId no válido en la solicitud: $request")
            return
        }
        
        logger.info("🛑 [SESION-$sessionId] Deteniendo monitoreo del dispositivo $deviceId")
        
        // Remover sesión específica
        val wasRemoved = activeSessions[deviceId]?.remove(sessionId) ?: false
        sessionActivity.remove(sessionId)
        
        if (!wasRemoved) {
            logger.warn("⚠️ [SESION-$sessionId] Sesión no encontrada para dispositivo $deviceId")
            return
        }
        
        // Si no hay más sesiones activas para este dispositivo, detener monitoreo
        if (activeSessions[deviceId]?.isEmpty() != false) {
            logger.info("🔌 [SESION-$sessionId] Última sesión para dispositivo $deviceId - Cerrando conexión MikroTik")
            
            runningTasks[deviceId]?.cancel(false)
            runningTasks.remove(deviceId)
            mikrotikConnectionService.closeConnection(deviceId)
            activeSessions.remove(deviceId)
            
            // Limpiar última muestra LAN para evitar cálculos incorrectos al reconectar
            lanLastSample.remove(deviceId)
            logger.info("🧹 [SESION-$sessionId] Limpiando última muestra LAN para dispositivo $deviceId")
            
            logger.info("✅ [SESION-$sessionId] Monitoreo detenido y conexión cerrada para dispositivo $deviceId")
        } else {
            logger.info("➖ [SESION-$sessionId] Sesión removida del dispositivo $deviceId - ${activeSessions[deviceId]?.size} sesiones restantes")
        }
        
        logCurrentStats()
    }
    
    /**
     * Maneja la desconexión de un usuario
     */
    override fun handleUserDisconnect(sessionId: String) {
        logger.info("🔌 [SESION-$sessionId] Usuario desconectado - Iniciando limpieza")
        
        // Encontrar todos los dispositivos que estaba monitoreando esta sesión
        val devicesToCheck = activeSessions.entries.filter { (_, sessions) -> 
            sessions.contains(sessionId) 
        }.map { it.key }
        
        logger.info("🔍 [SESION-$sessionId] Dispositivos a limpiar: ${devicesToCheck.joinToString()}")
        
        devicesToCheck.forEach { deviceId ->
            // Crear un header accessor temporal para la sesión
            val tempHeaderAccessor = SimpMessageHeaderAccessor.create()
            tempHeaderAccessor.sessionId = sessionId
            stopTrafficMonitor(mapOf("deviceId" to deviceId), tempHeaderAccessor)
        }
        
        sessionActivity.remove(sessionId)
        logger.info("✅ [SESION-$sessionId] Limpieza completada")
        logCurrentStats()
    }
    
    /**
     * Limpia sesiones inactivas
     */
    private fun cleanupInactiveSessions() {
        val currentTime = System.currentTimeMillis()
        val inactiveSessions = sessionActivity.entries.filter { (_, lastActivity) ->
            currentTime - lastActivity > SESSION_TIMEOUT_MS
        }.map { it.key }
        
        if (inactiveSessions.isNotEmpty()) {
            logger.info("🧹 Limpieza automática iniciada - ${inactiveSessions.size} sesiones inactivas encontradas")
            
            inactiveSessions.forEach { sessionId ->
                logger.info("⏰ [SESION-$sessionId] Sesión inactiva detectada - Removiendo")
                handleUserDisconnect(sessionId)
            }
            
            logger.info("✅ Limpieza automática completada - ${inactiveSessions.size} sesiones removidas")
        } else {
            logger.debug("🧹 Limpieza automática - No se encontraron sesiones inactivas")
        }
    }
    
    /**
     * Actualiza la actividad de una sesión
     */
    fun updateSessionActivity(sessionId: String) {
        sessionActivity[sessionId] = System.currentTimeMillis()
        logger.debug("🔄 [SESION-$sessionId] Actividad actualizada")
    }
    
    /**
     * Obtiene estadísticas de conexiones activas
     */
    fun getConnectionStats(): Map<String, Any> {
        val stats = mapOf(
            "activeDevices" to activeSessions.size,
            "totalSessions" to sessionActivity.size,
            "runningTasks" to runningTasks.size,
            "deviceSessions" to activeSessions.mapValues { it.value.size }
        )
        
        logger.info("📊 Estadísticas de conexiones: $stats")
        return stats
    }
    
    /**
     * Log de estadísticas actuales
     */
    private fun logCurrentStats() {
        val stats = getConnectionStats()
        logger.info("📈 Estado actual del sistema:")
        logger.info("   - Dispositivos activos: ${stats["activeDevices"]}")
        logger.info("   - Sesiones totales: ${stats["totalSessions"]}")
        logger.info("   - Tareas ejecutándose: ${stats["runningTasks"]}")
        logger.info("   - Sesiones por dispositivo: ${stats["deviceSessions"]}")
    }
} 