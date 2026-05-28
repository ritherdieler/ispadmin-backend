package com.dscorp.wispadmin.wispadmin.controller

import com.dscorp.wispadmin.wispadmin.data.model.NetworkDevice
import com.dscorp.wispadmin.wispadmin.dto.NetworkDeviceDto
import com.dscorp.wispadmin.wispadmin.repository.NetworkDeviceRepository
import com.dscorp.wispadmin.wispadmin.service.NetworkDeviceConnectionService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/networkDevice/connection")
class NetworkDeviceConnectionController {
    
    @Autowired
    lateinit var repository: NetworkDeviceRepository
    
    @Autowired
    lateinit var connectionService: NetworkDeviceConnectionService

    @GetMapping("/cloud-core-routers")
    fun getCloudCoreRouters(): ResponseEntity<List<NetworkDeviceDto>> {
        return try {
            val cloudCoreRouters = repository.findByNetworkDeviceType(NetworkDevice.NetworkDeviceType.CLOUD_CORE_ROUTER)
            ResponseEntity.status(200).body(cloudCoreRouters.map { it.toDto() })
        } catch (e: Exception) {
            e.printStackTrace()
            ResponseEntity.status(500).body(null)
        }
    }

    @GetMapping("/{deviceId}/interfaces")
    fun getDeviceInterfaces(@PathVariable deviceId: Int): ResponseEntity<Map<String, Any>> {
        return try {
            val device = repository.findById(deviceId).orElse(null)
            if (device == null) {
                return ResponseEntity.status(404).body(mapOf("error" to "Dispositivo no encontrado"))
            }
            
            val interfaces = connectionService.getDeviceInterfaces(device)
            ResponseEntity.status(200).body(mapOf(
                "device" to device.toDto(),
                "interfaces" to interfaces
            ))
        } catch (e: Exception) {
            e.printStackTrace()
            ResponseEntity.status(500).body(mapOf("error" to "Error al conectar con el dispositivo: ${e.message}"))
        }
    }

    @GetMapping("/{deviceId}/system-info")
    fun getDeviceSystemInfo(@PathVariable deviceId: Int): ResponseEntity<Map<String, Any>> {
        return try {
            val device = repository.findById(deviceId).orElse(null)
            if (device == null) {
                return ResponseEntity.status(404).body(mapOf("error" to "Dispositivo no encontrado"))
            }
            
            val systemInfo = connectionService.getDeviceSystemInfo(device)
            ResponseEntity.status(200).body(mapOf(
                "device" to device.toDto(),
                "systemInfo" to systemInfo
            ))
        } catch (e: Exception) {
            e.printStackTrace()
            ResponseEntity.status(500).body(mapOf("error" to "Error al conectar con el dispositivo: ${e.message}"))
        }
    }

    @GetMapping("/{deviceId}/resources")
    fun getDeviceResources(@PathVariable deviceId: Int): ResponseEntity<Map<String, Any>> {
        return try {
            val device = repository.findById(deviceId).orElse(null)
            if (device == null) {
                return ResponseEntity.status(404).body(mapOf("error" to "Dispositivo no encontrado"))
            }
            
            val resources = connectionService.getDeviceResources(device)
            ResponseEntity.status(200).body(mapOf(
                "device" to device.toDto(),
                "resources" to resources
            ))
        } catch (e: Exception) {
            e.printStackTrace()
            ResponseEntity.status(500).body(mapOf("error" to "Error al conectar con el dispositivo: ${e.message}"))
        }
    }

    @GetMapping("/{deviceId}/info")
    fun getDeviceInfo(@PathVariable deviceId: Int): ResponseEntity<Map<String, Any>> {
        return try {
            val device = repository.findById(deviceId).orElse(null)
            if (device == null) {
                return ResponseEntity.status(404).body(mapOf("error" to "Dispositivo no encontrado"))
            }
            
            val interfaces = connectionService.getDeviceInterfaces(device)
            val systemInfo = connectionService.getDeviceSystemInfo(device)
            val resources = connectionService.getDeviceResources(device)
            
            ResponseEntity.status(200).body(mapOf(
                "device" to device.toDto(),
                "interfaces" to interfaces,
                "systemInfo" to systemInfo,
                "resources" to resources
            ))
        } catch (e: Exception) {
            e.printStackTrace()
            ResponseEntity.status(500).body(mapOf("error" to "Error al conectar con el dispositivo: ${e.message}"))
        }
    }
} 
