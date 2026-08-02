package com.dscorp.wispadmin.wispadmin.service

import com.dscorp.wispadmin.routeros.RouterOsEntryId
import com.dscorp.wispadmin.routeros.config.RouterOsClientProperties
import com.dscorp.wispadmin.routeros.port.MikrotikClient
import com.dscorp.wispadmin.wispadmin.data.model.NetworkDevice
import com.dscorp.wispadmin.wispadmin.extensions.NetworkDeviceConnectionManager
import com.dscorp.wispadmin.wispadmin.service.mikrotik.MikrotikDeviceRefMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

@Service
class MikroTikConnectionService(
    private val mikrotikClient: MikrotikClient,
    private val routerOsClientProperties: RouterOsClientProperties
) {
    
    private val logger = LoggerFactory.getLogger(MikroTikConnectionService::class.java)
    
    private val scheduler: ScheduledExecutorService = Executors.newScheduledThreadPool(10)

    private fun isMockModeEnabled(): Boolean {
        return NetworkDeviceConnectionManager.isMikroTikMockModeEnabled()
    }

    private fun getMockPrint(path: String, query: Map<String, String> = emptyMap()): List<Map<String, String>> {
        return when {
            path == "/system/resource" -> listOf(
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
            path == "/system/identity" -> listOf(mapOf("name" to "mikrotik-mock"))
            path == "/system/package" && query["name"] == "routeros" -> listOf(
                mapOf(
                    "name" to "routeros",
                    "version" to "7.15.2",
                    "build-time" to "2026-03-01 10:00:00"
                )
            )
            path == "/interface" -> listOf(
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
            path == "/ip/firewall/address-list" && query["list"] == "deudores" -> listOf(
                mapOf(".id" to "*10", "list" to "deudores", "address" to "10.10.10.20", "comment" to "MOCK CLIENTE 1", "disabled" to "false"),
                mapOf(".id" to "*11", "list" to "deudores", "address" to "10.10.10.21", "comment" to "MOCK CLIENTE 2", "disabled" to "true")
            )
            path == "/ip/firewall/filter" -> listOf(
                mapOf(".id" to "*20", "comment" to "CORTADO POR DEUDA - MOCK", "disabled" to "false")
            )
            else -> emptyList()
        }
    }

    fun printOnDevice(
        device: NetworkDevice,
        path: String,
        query: Map<String, String> = emptyMap()
    ): List<Map<String, String>> {
        logger.info("🔧 [DISPOSITIVO-${device.id}] REST print $path query=$query")
        if (isMockModeEnabled()) {
            return getMockPrint(path, query)
        }
        return try {
            val deviceRef = MikrotikDeviceRefMapper.toDeviceRef(device, routerOsClientProperties.classic.port)
            mikrotikClient.withSession(deviceRef) { session ->
                session.print(path, query)
            }.also { result ->
                logger.info("✅ [DISPOSITIVO-${device.id}] print OK - ${result.size} resultados")
            }
        } catch (e: Exception) {
            logger.error("❌ [DISPOSITIVO-${device.id}] Error en print: ${e.message}")
            throw e
        }
    }

    fun setOnDevice(device: NetworkDevice, path: String, id: String, args: Map<String, String>) {
        logger.info("🔧 [DISPOSITIVO-${device.id}] REST set $path id=$id args=$args")
        if (isMockModeEnabled()) {
            return
        }
        val deviceRef = MikrotikDeviceRefMapper.toDeviceRef(device, routerOsClientProperties.classic.port)
        mikrotikClient.withSession(deviceRef) { session ->
            session.set(path, id, args)
        }
    }

    @Deprecated("Use printOnDevice", ReplaceWith("printOnDevice(device, path, query)"))
    fun executeSingleCommand(device: NetworkDevice, command: String): List<Map<String, String>> {
        return legacyCommandToPrint(device, command)
    }

    private fun legacyCommandToPrint(device: NetworkDevice, command: String): List<Map<String, String>> {
        return when {
            command == "/interface/print" -> printOnDevice(device, "/interface")
            command == "/system/resource/print" -> printOnDevice(device, "/system/resource")
            command == "/system/identity/print" -> printOnDevice(device, "/system/identity")
            command == "/ip/firewall/filter/print" -> printOnDevice(device, "/ip/firewall/filter")
            command.startsWith("/system/package/print where name=routeros") ->
                printOnDevice(device, "/system/package", mapOf("name" to "routeros"))
            command.startsWith("/ip/firewall/address-list/print where list=deudores") ->
                printOnDevice(device, "/ip/firewall/address-list", mapOf("list" to "deudores"))
            else -> throw IllegalArgumentException("Legacy MikroTik command not mapped to REST: $command")
        }
    }

    fun executeCommand(device: NetworkDevice, command: String): List<Map<String, String>> {
        return try {
            legacyCommandToPrint(device, command)
        } catch (e: Exception) {
            logger.error("❌ [DISPOSITIVO-${device.id}] Error ejecutando comando: ${e.message}")
            emptyList()
        }
    }
    
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
                    Thread.sleep(5000)
                }
            }
            
            logger.info("🏁 [DISPOSITIVO-$deviceId] Thread de monitoreo finalizado")
        }
    }
    
    fun scheduleMonitoring(
        device: NetworkDevice,
        path: String,
        query: Map<String, String> = emptyMap(),
        intervalMs: Long,
        onData: (List<Map<String, String>>) -> Unit,
        shouldContinue: () -> Boolean = { true }
    ): ScheduledFuture<*> {
        val deviceId = device.id
        logger.info("📅 [DISPOSITIVO-$deviceId] Programando monitoreo REST $path - Intervalo: ${intervalMs}ms")

        return scheduler.scheduleWithFixedDelay({
            try {
                if (shouldContinue()) {
                    val result = printOnDevice(device, path, query)
                    onData(result)
                } else {
                    logger.info("⏹️ [DISPOSITIVO-$deviceId] Condición de continuidad falsa, deteniendo monitoreo")
                }
            } catch (e: Exception) {
                logger.error("❌ [DISPOSITIVO-$deviceId] Error en monitoreo programado: ${e.message}")
            }
        }, intervalMs, intervalMs, TimeUnit.MILLISECONDS).also {
            logger.info("✅ [DISPOSITIVO-$deviceId] Monitoreo programado exitosamente")
        }
    }

    fun scheduleMonitoringLegacyCommand(
        device: NetworkDevice,
        command: String,
        intervalMs: Long,
        onData: (List<Map<String, String>>) -> Unit,
        shouldContinue: () -> Boolean = { true }
    ): ScheduledFuture<*> {
        return scheduleMonitoring(
            device = device,
            path = when (command) {
                "/interface/print" -> "/interface"
                "/system/resource/print" -> "/system/resource"
                else -> throw IllegalArgumentException("Unsupported monitoring command: $command")
            },
            intervalMs = intervalMs,
            onData = onData,
            shouldContinue = shouldContinue
        )
    }
    
    fun closeConnection(deviceId: Int) {
        logger.info("🔌 [DISPOSITIVO-$deviceId] Cerrando conexión")
        mikrotikClient.closeSession(deviceId.toString())
        logger.info("✅ [DISPOSITIVO-$deviceId] Conexión cerrada exitosamente")
    }
    
    fun closeAllConnections() {
        val deviceIds = mikrotikClient.activeSessionDeviceIds()
        logger.info("🔌 Cerrando todas las conexiones activas (${deviceIds.size} conexiones)")
        deviceIds.forEach { deviceId ->
            mikrotikClient.closeSession(deviceId)
        }
        scheduler.shutdown()
        logger.info("✅ Todas las conexiones cerradas y scheduler detenido")
    }
    
    fun isConnectionActive(deviceId: Int): Boolean {
        val isActive = mikrotikClient.isSessionActive(deviceId.toString())
        logger.debug("🔍 [DISPOSITIVO-$deviceId] Conexión activa: $isActive")
        return isActive
    }
    
    fun getConnectionStats(): Map<String, Any> {
        val deviceIds = mikrotikClient.activeSessionDeviceIds()
        val stats = mapOf(
            "activeConnections" to deviceIds.size,
            "connectionLocks" to deviceIds.size,
            "schedulerActive" to !scheduler.isShutdown,
            "connectedDevices" to deviceIds.mapNotNull { it.toIntOrNull() }
        )
        
        logger.info("📊 Estadísticas de conexiones MikroTik: $stats")
        return stats
    }
    
    fun getDebtCutFilterRules(device: NetworkDevice): List<Map<String, String>> {
        logger.info("🔍 [DISPOSITIVO-${device.id}] Obteniendo filter rules de CORTADO POR DEUDA")
        
        return try {
            val allRules = printOnDevice(device, "/ip/firewall/filter")
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

    fun getDebtorsAddressList(device: NetworkDevice): List<Map<String, String>> {
        logger.info("🔍 [DISPOSITIVO-${device.id}] Obteniendo address-list 'deudores'")
        return try {
            val entries = printOnDevice(device, "/ip/firewall/address-list", mapOf("list" to "deudores"))
            logger.info("✅ [DISPOSITIVO-${device.id}] Encontradas ${entries.size} entradas en 'deudores'")
            entries
        } catch (e: Exception) {
            logger.error("❌ [DISPOSITIVO-${device.id}] Error obteniendo address-list deudores: ${e.message}")
            emptyList()
        }
    }

    fun enableAddressListEntry(device: NetworkDevice, entryId: String): Boolean {
        val normalizedId = RouterOsEntryId.normalize(entryId)
        logger.info("✅ [DISPOSITIVO-${device.id}] Habilitando address-list entry ID: $normalizedId en 'deudores'")
        return try {
            setOnDevice(device, "/ip/firewall/address-list", normalizedId, mapOf("disabled" to "no"))
            true
        } catch (e: Exception) {
            logger.error("❌ [DISPOSITIVO-${device.id}] Error habilitando address-list entry $entryId: ${e.message}")
            false
        }
    }

    fun disableAddressListEntry(device: NetworkDevice, entryId: String): Boolean {
        val normalizedId = RouterOsEntryId.normalize(entryId)
        logger.info("🚫 [DISPOSITIVO-${device.id}] Deshabilitando address-list entry ID: $normalizedId en 'deudores'")
        return try {
            setOnDevice(device, "/ip/firewall/address-list", normalizedId, mapOf("disabled" to "yes"))
            true
        } catch (e: Exception) {
            logger.error("❌ [DISPOSITIVO-${device.id}] Error deshabilitando address-list entry $entryId: ${e.message}")
            false
        }
    }

    fun enableMultipleAddressListEntries(device: NetworkDevice, entryIds: List<String>): Map<String, Boolean> {
        logger.info("✅ [DISPOSITIVO-${device.id}] Habilitando ${entryIds.size} address-list entries en 'deudores'")
        val results = mutableMapOf<String, Boolean>()
        entryIds.forEach { id -> results[RouterOsEntryId.normalize(id)] = enableAddressListEntry(device, id) }
        val successCount = results.values.count { it }
        logger.info("📊 [DISPOSITIVO-${device.id}] Habilitación completada: $successCount/${entryIds.size} exitosos")
        return results
    }

    fun disableMultipleAddressListEntries(device: NetworkDevice, entryIds: List<String>): Map<String, Boolean> {
        logger.info("🚫 [DISPOSITIVO-${device.id}] Deshabilitando ${entryIds.size} address-list entries en 'deudores'")
        val results = mutableMapOf<String, Boolean>()
        entryIds.forEach { id -> results[RouterOsEntryId.normalize(id)] = disableAddressListEntry(device, id) }
        val successCount = results.values.count { it }
        logger.info("📊 [DISPOSITIVO-${device.id}] Deshabilitación completada: $successCount/${entryIds.size} exitosos")
        return results
    }
    
    fun enableFilterRule(device: NetworkDevice, ruleId: String): Boolean {
        logger.info("✅ [DISPOSITIVO-${device.id}] Activando filter rule ID: $ruleId")
        
        return try {
            setOnDevice(device, "/ip/firewall/filter", ruleId, mapOf("disabled" to "no"))
            logger.info("✅ [DISPOSITIVO-${device.id}] Filter rule $ruleId activado exitosamente")
            true
        } catch (e: Exception) {
            logger.error("❌ [DISPOSITIVO-${device.id}] Error activando filter rule $ruleId: ${e.message}")
            false
        }
    }
    
    fun disableFilterRule(device: NetworkDevice, ruleId: String): Boolean {
        logger.info("🚫 [DISPOSITIVO-${device.id}] Desactivando filter rule ID: $ruleId")
        
        return try {
            setOnDevice(device, "/ip/firewall/filter", ruleId, mapOf("disabled" to "yes"))
            logger.info("✅ [DISPOSITIVO-${device.id}] Filter rule $ruleId desactivado exitosamente")
            true
        } catch (e: Exception) {
            logger.error("❌ [DISPOSITIVO-${device.id}] Error desactivando filter rule $ruleId: ${e.message}")
            false
        }
    }
    
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
    
    fun disableMultipleFilterRules(device: NetworkDevice, ruleIds: List<String>): Map<String, Boolean> {
        logger.info("🚫 [DISPOSITIVO-${device.id}] Desactivando ${ruleIds.size} filter rules")
        
        val results = mutableMapOf<String, Boolean>()
        ruleIds.forEach { ruleId ->
            results[ruleId] = disableFilterRule(device, ruleId)
        }
        
        val successCount = results.values.count { it }
        logger.info("📊 [DISPOSITIVO-${device.id}] Deshabilitación completada: $successCount/${ruleIds.size} exitosos")
        return results
    }
}
