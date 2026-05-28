package com.dscorp.wispadmin.wispadmin.service

import com.dscorp.wispadmin.wispadmin.data.model.NetworkDevice
import com.dscorp.wispadmin.wispadmin.extensions.NetworkDeviceConnectionManager
import me.legrange.mikrotik.ApiConnection
import me.legrange.mikrotik.MikrotikApiException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import java.util.concurrent.ScheduledFuture

@Service
class MikroTikConnectionService {
    
    private val logger = LoggerFactory.getLogger(MikroTikConnectionService::class.java)
    
    private val connections = ConcurrentHashMap<Int, ApiConnection>()
    private val connectionLocks = ConcurrentHashMap<Int, Any>()
    private val scheduler: ScheduledExecutorService = Executors.newScheduledThreadPool(10)

    private fun isMockModeEnabled(): Boolean {
        return NetworkDeviceConnectionManager.isMikroTikMockModeEnabled()
    }

    private fun getMockResponse(command: String): List<Map<String, String>> {
        return when {
            command.startsWith("/system/resource/print") -> listOf(
                mapOf(
                    "cpu-load" to "17",
                    "cpu-count" to "4",
                    "cpu-frequency" to "1200",
                    "free-memory" to "314572800",
                    "total-memory" to "536870912",
                    "free-hdd-space" to "2147483648",
                    "total-hdd-space" to "4294967296",
                    "uptime" to "2d03:18:22",
                    "version" to "7.15.2",
                    "board-name" to "RB750Gr3",
                    "architecture-name" to "arm"
                )
            )
            command.startsWith("/system/identity/print") -> listOf(
                mapOf("name" to "mikrotik-mock")
            )
            command.startsWith("/system/package/print") -> listOf(
                mapOf(
                    "name" to "routeros",
                    "version" to "7.15.2",
                    "build-time" to "2026-03-01 10:00:00"
                )
            )
            command.startsWith("/interface/print") -> listOf(
                mapOf(
                    ".id" to "*1",
                    "name" to "ether1",
                    "type" to "ether",
                    "running" to "true",
                    "disabled" to "false",
                    "rx-byte" to "125000000",
                    "tx-byte" to "98000000",
                    "rx-packet" to "155000",
                    "tx-packet" to "131000",
                    "mtu" to "1500",
                    "mac-address" to "AA:BB:CC:DD:EE:01"
                ),
                mapOf(
                    ".id" to "*2",
                    "name" to "lan",
                    "type" to "bridge",
                    "running" to "true",
                    "disabled" to "false",
                    "rx-byte" to "420000000",
                    "tx-byte" to "390000000",
                    "rx-packet" to "525000",
                    "tx-packet" to "510000",
                    "mtu" to "1500",
                    "mac-address" to "AA:BB:CC:DD:EE:02"
                )
            )
            command.startsWith("/ip/firewall/address-list/print where list=deudores") -> listOf(
                mapOf(".id" to "*10", "list" to "deudores", "address" to "10.10.10.20", "comment" to "MOCK CLIENTE 1", "disabled" to "false"),
                mapOf(".id" to "*11", "list" to "deudores", "address" to "10.10.10.21", "comment" to "MOCK CLIENTE 2", "disabled" to "true")
            )
            command.startsWith("/ip/firewall/filter/print") -> listOf(
                mapOf(".id" to "*20", "comment" to "CORTADO POR DEUDA - MOCK", "disabled" to "false")
            )
            else -> emptyList()
        }
    }
    
    /**
     * Ejecuta un comando de una sola lectura (abre y cierra la conexión automáticamente)
     * Útil para comandos que se ejecutan una sola vez como obtener información del sistema, recursos, etc.
     */
    fun executeSingleCommand(device: NetworkDevice, command: String): List<Map<String, String>> {
        logger.info("🔧 [DISPOSITIVO-${device.id}] Ejecutando comando único: $command")
        if (isMockModeEnabled()) {
            return getMockResponse(command)
        }
        val connectionData = NetworkDeviceConnectionManager.getConnectionData(device)
        
        return try {
            logger.debug("🔗 [DISPOSITIVO-${device.id}] Abriendo conexión temporal a ${connectionData.ipAddress}")
            val connection = ApiConnection.connect(connectionData.ipAddress!!)
            connection.login(connectionData.username!!, connectionData.password!!)
            
            logger.debug("📡 [DISPOSITIVO-${device.id}] Ejecutando comando...")
            val result = connection.execute(command)
            
            connection.close()
            logger.info("✅ [DISPOSITIVO-${device.id}] Comando ejecutado exitosamente - ${result.size} resultados")
            
            result
        } catch (e: MikrotikApiException) {
            logger.error("❌ [DISPOSITIVO-${device.id}] Error MikroTik ejecutando comando único: ${e.message}")
            throw e // Propagar la excepción en lugar de retornar lista vacía
        } catch (e: Exception) {
            logger.error("❌ [DISPOSITIVO-${device.id}] Error inesperado ejecutando comando único: ${e.message}")
            throw e // Propagar la excepción en lugar de retornar lista vacía
        }
    }
    
    /**
     * Obtiene una conexión persistente a un dispositivo MikroTik
     */
    fun getConnection(device: NetworkDevice): ApiConnection? {
        val deviceId = device.id
        val connectionData = NetworkDeviceConnectionManager.getConnectionData(device)
        
        // Si ya existe una conexión, retornarla
        connections[deviceId]?.let { 
            logger.debug("🔗 [DISPOSITIVO-$deviceId] Reutilizando conexión existente")
            return it 
        }
        
        // Si no existe, crear una nueva
        logger.info("🔗 [DISPOSITIVO-$deviceId] Creando nueva conexión persistente a ${connectionData.ipAddress}")
        
        return try {
            val connection = ApiConnection.connect(connectionData.ipAddress!!)
            connection.login(connectionData.username!!, connectionData.password!!)
            connections[deviceId] = connection
            
            logger.info("✅ [DISPOSITIVO-$deviceId] Conexión persistente establecida exitosamente")
            connection
        } catch (e: MikrotikApiException) {
            logger.error("❌ [DISPOSITIVO-$deviceId] Error MikroTik creando conexión: ${e.message}")
            null
        } catch (e: Exception) {
            logger.error("❌ [DISPOSITIVO-$deviceId] Error inesperado creando conexión: ${e.message}")
            null
        }
    }
    
    /**
     * Ejecuta un comando en una conexión persistente
     */
    fun executeCommand(device: NetworkDevice, command: String): List<Map<String, String>> {
        val deviceId = device.id
        if (isMockModeEnabled()) {
            return getMockResponse(command)
        }
        val lock = connectionLocks.computeIfAbsent(deviceId) { Any() }
        
        synchronized(lock) {
            val connection = getConnection(device) ?: run {
                logger.error("❌ [DISPOSITIVO-$deviceId] No se pudo obtener conexión para ejecutar comando")
                return emptyList()
            }
            
            return try {
                logger.debug("📡 [DISPOSITIVO-$deviceId] Ejecutando comando en conexión persistente: $command")
                val result = connection.execute(command)
                logger.debug("✅ [DISPOSITIVO-$deviceId] Comando ejecutado exitosamente - ${result.size} resultados")
                result
            } catch (e: MikrotikApiException) {
                logger.error("❌ [DISPOSITIVO-$deviceId] Error MikroTik ejecutando comando: ${e.message}")
                logger.info("🔄 [DISPOSITIVO-$deviceId] Intentando reconectar...")
                
                // Intentar reconectar
                closeConnection(deviceId)
                val newConnection = getConnection(device)
                newConnection?.execute(command) ?: run {
                    logger.error("❌ [DISPOSITIVO-$deviceId] Falló la reconexión")
                    emptyList()
                }
            } catch (e: Exception) {
                logger.error("❌ [DISPOSITIVO-$deviceId] Error inesperado ejecutando comando: ${e.message}")
                emptyList()
            }
        }
    }
    
    /**
     * Ejecuta un comando de larga duración (monitoreo continuo)
     */
    fun executeLongRunningCommand(
        device: NetworkDevice, 
        command: String, 
        intervalMs: Long,
        onData: (List<Map<String, String>>) -> Unit,
        shouldContinue: () -> Boolean = { true }
    ): Runnable {
        val deviceId = device.id
        logger.info("🔄 [DISPOSITIVO-$deviceId] Iniciando comando de larga duración - Intervalo: ${intervalMs}ms")
        
        return Runnable {
            logger.info("🚀 [DISPOSITIVO-$deviceId] Thread de monitoreo iniciado")
            
            while (!Thread.currentThread().isInterrupted() && shouldContinue()) {
                try {
                    val result = executeCommand(device, command)
                    onData(result)
                    Thread.sleep(intervalMs)
                } catch (e: InterruptedException) {
                    logger.info("⏹️ [DISPOSITIVO-$deviceId] Thread de monitoreo interrumpido")
                    break
                } catch (e: Exception) {
                    logger.error("❌ [DISPOSITIVO-$deviceId] Error en comando de larga duración: ${e.message}")
                    logger.info("⏳ [DISPOSITIVO-$deviceId] Esperando 5 segundos antes de reintentar...")
                    Thread.sleep(5000) // Esperar antes de reintentar
                }
            }
            
            logger.info("🏁 [DISPOSITIVO-$deviceId] Thread de monitoreo finalizado")
        }
    }
    
    /**
     * Programa una tarea de monitoreo continuo
     */
    fun scheduleMonitoring(
        device: NetworkDevice,
        command: String,
        intervalMs: Long,
        onData: (List<Map<String, String>>) -> Unit,
        shouldContinue: () -> Boolean = { true }
    ): ScheduledFuture<*> {
        val deviceId = device.id
        logger.info("📅 [DISPOSITIVO-$deviceId] Programando monitoreo continuo - Intervalo: ${intervalMs}ms")
        
        // Cambiar a scheduleWithFixedDelay para evitar ejecuciones concurrentes
        // y agregar delay inicial para evitar ejecución inmediata duplicada
        val scheduledTask = scheduler.scheduleWithFixedDelay({
            try {
                if (shouldContinue()) {
                    val result = executeCommand(device, command)
                    onData(result)
                } else {
                    logger.info("⏹️ [DISPOSITIVO-$deviceId] Condición de continuidad falsa, deteniendo monitoreo")
                }
            } catch (e: Exception) {
                logger.error("❌ [DISPOSITIVO-$deviceId] Error en monitoreo programado: ${e.message}")
            }
        }, intervalMs, intervalMs, TimeUnit.MILLISECONDS) // Delay inicial = intervalo
        
        logger.info("✅ [DISPOSITIVO-$deviceId] Monitoreo programado exitosamente con delay inicial")
        return scheduledTask
    }
    
    /**
     * Cierra una conexión específica
     */
    fun closeConnection(deviceId: Int) {
        logger.info("🔌 [DISPOSITIVO-$deviceId] Cerrando conexión")
        
        connections[deviceId]?.let { connection ->
            try {
                connection.close()
                logger.info("✅ [DISPOSITIVO-$deviceId] Conexión cerrada exitosamente")
            } catch (e: Exception) {
                logger.error("❌ [DISPOSITIVO-$deviceId] Error cerrando conexión: ${e.message}")
            } finally {
                connections.remove(deviceId)
                connectionLocks.remove(deviceId)
                logger.info("🧹 [DISPOSITIVO-$deviceId] Recursos de conexión liberados")
            }
        } ?: run {
            logger.warn("⚠️ [DISPOSITIVO-$deviceId] No se encontró conexión para cerrar")
        }
    }
    
    /**
     * Cierra todas las conexiones
     */
    fun closeAllConnections() {
        logger.info("🔌 Cerrando todas las conexiones activas (${connections.size} conexiones)")
        
        connections.keys.forEach { deviceId ->
            closeConnection(deviceId)
        }
        
        scheduler.shutdown()
        logger.info("✅ Todas las conexiones cerradas y scheduler detenido")
    }
    
    /**
     * Verifica si una conexión está activa
     */
    fun isConnectionActive(deviceId: Int): Boolean {
        val isActive = connections.containsKey(deviceId)
        logger.debug("🔍 [DISPOSITIVO-$deviceId] Conexión activa: $isActive")
        return isActive
    }
    
    /**
     * Obtiene estadísticas de conexiones
     */
    fun getConnectionStats(): Map<String, Any> {
        val stats = mapOf(
            "activeConnections" to connections.size,
            "connectionLocks" to connectionLocks.size,
            "schedulerActive" to !scheduler.isShutdown,
            "connectedDevices" to connections.keys.toList()
        )
        
        logger.info("📊 Estadísticas de conexiones MikroTik: $stats")
        return stats
    }
    
    /**
     * Obtiene todos los filter rules que comienzan con "CORTADO POR DEUDA"
     */
    fun getDebtCutFilterRules(device: NetworkDevice): List<Map<String, String>> {
        logger.info("🔍 [DISPOSITIVO-${device.id}] Obteniendo filter rules de CORTADO POR DEUDA")
        
        return try {
            val allRules = executeSingleCommand(device, "/ip/firewall/filter/print")
            val debtRules = allRules.filter { rule ->
                val comment = rule["comment"] ?: ""
                comment.startsWith("CORTADO POR DEUDA", ignoreCase = true)
            }
            
            logger.info("✅ [DISPOSITIVO-${device.id}] Encontrados ${debtRules.size} filter rules de CORTADO POR DEUDA")
            debtRules
        } catch (e: Exception) {
            logger.error("❌ [DISPOSITIVO-${device.id}] Error obteniendo filter rules de deuda: ${e.message}")
            emptyList()
        }
    }

    /**
     * Obtiene todas las entradas de la address-list "deudores"
     */
    fun getDebtorsAddressList(device: NetworkDevice): List<Map<String, String>> {
        logger.info("🔍 [DISPOSITIVO-${device.id}] Obteniendo address-list 'deudores'")
        return try {
            val entries = executeSingleCommand(device, "/ip/firewall/address-list/print where list=deudores")
            logger.info("✅ [DISPOSITIVO-${device.id}] Encontradas ${entries.size} entradas en 'deudores'")
            entries
        } catch (e: Exception) {
            logger.error("❌ [DISPOSITIVO-${device.id}] Error obteniendo address-list deudores: ${e.message}")
            emptyList()
        }
    }

    /**
     * Habilita una entrada específica de address-list por ID (disabled=no)
     */
    fun enableAddressListEntry(device: NetworkDevice, entryId: String): Boolean {
        logger.info("✅ [DISPOSITIVO-${device.id}] Habilitando address-list entry ID: $entryId en 'deudores'")
        return try {
            executeSingleCommand(device, "/ip/firewall/address-list/set .id=$entryId disabled=no")
            true
        } catch (e: Exception) {
            logger.error("❌ [DISPOSITIVO-${device.id}] Error habilitando address-list entry $entryId: ${e.message}")
            false
        }
    }

    /**
     * Deshabilita una entrada específica de address-list por ID (disabled=yes)
     */
    fun disableAddressListEntry(device: NetworkDevice, entryId: String): Boolean {
        logger.info("🚫 [DISPOSITIVO-${device.id}] Deshabilitando address-list entry ID: $entryId en 'deudores'")
        return try {
            executeSingleCommand(device, "/ip/firewall/address-list/set .id=$entryId disabled=yes")
            true
        } catch (e: Exception) {
            logger.error("❌ [DISPOSITIVO-${device.id}] Error deshabilitando address-list entry $entryId: ${e.message}")
            false
        }
    }

    /**
     * Habilita múltiples entradas de address-list por sus IDs
     */
    fun enableMultipleAddressListEntries(device: NetworkDevice, entryIds: List<String>): Map<String, Boolean> {
        logger.info("✅ [DISPOSITIVO-${device.id}] Habilitando ${entryIds.size} address-list entries en 'deudores'")
        val results = mutableMapOf<String, Boolean>()
        entryIds.forEach { id -> results[id] = enableAddressListEntry(device, id) }
        val successCount = results.values.count { it }
        logger.info("📊 [DISPOSITIVO-${device.id}] Habilitación completada: $successCount/${entryIds.size} exitosos")
        return results
    }

    /**
     * Deshabilita múltiples entradas de address-list por sus IDs
     */
    fun disableMultipleAddressListEntries(device: NetworkDevice, entryIds: List<String>): Map<String, Boolean> {
        logger.info("🚫 [DISPOSITIVO-${device.id}] Deshabilitando ${entryIds.size} address-list entries en 'deudores'")
        val results = mutableMapOf<String, Boolean>()
        entryIds.forEach { id -> results[id] = disableAddressListEntry(device, id) }
        val successCount = results.values.count { it }
        logger.info("📊 [DISPOSITIVO-${device.id}] Deshabilitación completada: $successCount/${entryIds.size} exitosos")
        return results
    }
    
    /**
     * Activa un filter rule específico por ID
     */
    fun enableFilterRule(device: NetworkDevice, ruleId: String): Boolean {
        logger.info("✅ [DISPOSITIVO-${device.id}] Activando filter rule ID: $ruleId")
        
        return try {
            // Usar el comando /set con .id y disabled según la documentación de MikroTik API
            executeSingleCommand(device, "/ip/firewall/filter/set .id=$ruleId disabled=no")
            logger.info("✅ [DISPOSITIVO-${device.id}] Filter rule $ruleId activado exitosamente")
            true
        } catch (e: Exception) {
            logger.error("❌ [DISPOSITIVO-${device.id}] Error activando filter rule $ruleId: ${e.message}")
            false
        }
    }
    
    /**
     * Desactiva un filter rule específico por ID
     */
    fun disableFilterRule(device: NetworkDevice, ruleId: String): Boolean {
        logger.info("🚫 [DISPOSITIVO-${device.id}] Desactivando filter rule ID: $ruleId")
        
        return try {
            // Usar el comando /set con .id y disabled según la documentación de MikroTik API
            executeSingleCommand(device, "/ip/firewall/filter/set .id=$ruleId disabled=yes")
            logger.info("✅ [DISPOSITIVO-${device.id}] Filter rule $ruleId desactivado exitosamente")
            true
        } catch (e: Exception) {
            logger.error("❌ [DISPOSITIVO-${device.id}] Error desactivando filter rule $ruleId: ${e.message}")
            false
        }
    }
    
    /**
     * Activa múltiples filter rules por sus IDs
     */
    fun enableMultipleFilterRules(device: NetworkDevice, ruleIds: List<String>): Map<String, Boolean> {
        logger.info("✅ [DISPOSITIVO-${device.id}] Activando ${ruleIds.size} filter rules")
        
        val results = mutableMapOf<String, Boolean>()
        ruleIds.forEach { ruleId ->
            results[ruleId] = enableFilterRule(device, ruleId)
        }
        
        val successCount = results.values.count { it }
        logger.info("📊 [DISPOSITIVO-${device.id}] Activación completada: $successCount/${ruleIds.size} exitosos")
        return results
    }
    
    /**
     * Desactiva múltiples filter rules por sus IDs
     */
    fun disableMultipleFilterRules(device: NetworkDevice, ruleIds: List<String>): Map<String, Boolean> {
        logger.info("🚫 [DISPOSITIVO-${device.id}] Desactivando ${ruleIds.size} filter rules")
        
        val results = mutableMapOf<String, Boolean>()
        ruleIds.forEach { ruleId ->
            results[ruleId] = disableFilterRule(device, ruleId)
        }
        
        val successCount = results.values.count { it }
        logger.info("📊 [DISPOSITIVO-${device.id}] Desactivación completada: $successCount/${ruleIds.size} exitosos")
        return results
    }
} 