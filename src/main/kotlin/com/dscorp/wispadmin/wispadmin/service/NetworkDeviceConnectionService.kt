package com.dscorp.wispadmin.wispadmin.service

import com.dscorp.wispadmin.wispadmin.data.model.NetworkDevice
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
class NetworkDeviceConnectionService {

    @Autowired
    private lateinit var mikrotikConnectionService: MikroTikConnectionService

    /**
     * Obtiene información de las interfaces del dispositivo
     */
    fun getDeviceInterfaces(device: NetworkDevice): List<Map<String, String>> {
        return mikrotikConnectionService.executeSingleCommand(device, "/interface/print")
            .filter { interfaceInfo -> 
                // Filtrar interfaces de tipo pppoe-in
                interfaceInfo["type"] != "pppoe-in"
            }
            .map { interfaceInfo ->
                mapOf(
                    "name" to (interfaceInfo["name"] ?: ""),
                    "type" to (interfaceInfo["type"] ?: ""),
                    "mtu" to (interfaceInfo["mtu"] ?: ""),
                    "macAddress" to (interfaceInfo["mac-address"] ?: ""),
                    "running" to (interfaceInfo["running"] ?: ""),
                    "disabled" to (interfaceInfo["disabled"] ?: ""),
                    "comment" to (interfaceInfo["comment"] ?: "")
                )
            }
    }

    /**
     * Obtiene información del sistema del dispositivo
     */
    fun getDeviceSystemInfo(device: NetworkDevice): Map<String, String> {
        val systemInfo = mikrotikConnectionService.executeSingleCommand(device, "/system/resource/print").firstOrNull() ?: emptyMap()
        val identityInfo = mikrotikConnectionService.executeSingleCommand(device, "/system/identity/print").firstOrNull() ?: emptyMap()
        val versionInfo = mikrotikConnectionService.executeSingleCommand(device, "/system/package/print where name=routeros").firstOrNull() ?: emptyMap()
        
        return mapOf(
            "identity" to (identityInfo["name"] ?: ""),
            "version" to (versionInfo["version"] ?: ""),
            "cpuLoad" to (systemInfo["cpu-load"] ?: ""),
            "freeMemory" to (systemInfo["free-memory"] ?: ""),
            "totalMemory" to (systemInfo["total-memory"] ?: ""),
            "freeHddSpace" to (systemInfo["free-hdd-space"] ?: ""),
            "totalHddSpace" to (systemInfo["total-hdd-space"] ?: ""),
            "uptime" to (systemInfo["uptime"] ?: ""),
            "buildTime" to (versionInfo["build-time"] ?: "")
        )
    }

    /**
     * Obtiene información de recursos del dispositivo
     */
    fun getDeviceResources(device: NetworkDevice): Map<String, Any> {
        val systemInfo = mikrotikConnectionService.executeSingleCommand(device, "/system/resource/print").firstOrNull() ?: emptyMap()
        
        val memoryUsage = try {
            val totalMemory = (systemInfo["total-memory"] ?: "0").toLong()
            val freeMemory = (systemInfo["free-memory"] ?: "0").toLong()
            val usedMemory = totalMemory - freeMemory
            val memoryPercentage = if (totalMemory > 0) (usedMemory * 100 / totalMemory) else 0
            mapOf(
                "total" to totalMemory,
                "free" to freeMemory,
                "used" to usedMemory,
                "percentage" to memoryPercentage
            )
        } catch (e: Exception) {
            mapOf("error" to "Error calculando uso de memoria")
        }
        
        val diskUsage = try {
            val totalHdd = (systemInfo["total-hdd-space"] ?: "0").toLong()
            val freeHdd = (systemInfo["free-hdd-space"] ?: "0").toLong()
            val usedHdd = totalHdd - freeHdd
            val diskPercentage = if (totalHdd > 0) (usedHdd * 100 / totalHdd) else 0
            mapOf(
                "total" to totalHdd,
                "free" to freeHdd,
                "used" to usedHdd,
                "percentage" to diskPercentage
            )
        } catch (e: Exception) {
            mapOf("error" to "Error calculando uso de disco")
        }
        
        return mapOf(
            "cpu" to mapOf(
                "load" to (systemInfo["cpu-load"] ?: "0"),
                "count" to (systemInfo["cpu-count"] ?: "1"),
                "frequency" to (systemInfo["cpu-frequency"] ?: "0")
            ),
            "memory" to memoryUsage,
            "disk" to diskUsage,
            "uptime" to (systemInfo["uptime"] ?: "0"),
            "version" to (systemInfo["version"] ?: ""),
            "boardName" to (systemInfo["board-name"] ?: ""),
            "architecture" to (systemInfo["architecture-name"] ?: "")
        )
    }

    /**
     * Ejecuta un comando personalizado en el dispositivo
     */
    fun executeCustomCommand(device: NetworkDevice, command: String): List<Map<String, String>> {
        return try {
            mikrotikConnectionService.executeSingleCommand(device, command)
        } catch (e: Exception) {
            listOf(mapOf("error" to (e.message ?: "Error ejecutando comando")))
        }
    }
} 