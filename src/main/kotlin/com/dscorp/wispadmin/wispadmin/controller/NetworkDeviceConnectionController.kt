package com.dscorp.wispadmin.wispadmin.controller

import com.dscorp.wispadmin.wispadmin.dto.NetworkDeviceDto
import com.dscorp.wispadmin.wispadmin.repository.NetworkDeviceRepository
import com.dscorp.wispadmin.wispadmin.service.NetworkDeviceConnectionService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/networkDevice/connection")
class NetworkDeviceConnectionController(
    private val repository: NetworkDeviceRepository,
    private val connectionService: NetworkDeviceConnectionService
) {

    @GetMapping("/cloud-core-routers")
    fun getCloudCoreRouters(): ResponseEntity<List<NetworkDeviceDto>> =
        ResponseEntity.ok(repository.findActiveCloudCoreRouters().map { it.toDto() })

    @GetMapping("/{deviceId}/interfaces")
    fun getDeviceInterfaces(@PathVariable deviceId: Int): ResponseEntity<Map<String, Any>> {
        val device = repository.findById(deviceId).orElse(null)
            ?: return ResponseEntity.status(404).body(mapOf("error" to "Dispositivo no encontrado"))
        val interfaces = connectionService.getDeviceInterfaces(device)
        return ResponseEntity.ok(mapOf("device" to device.toDto(), "interfaces" to interfaces))
    }

    @GetMapping("/{deviceId}/system-info")
    fun getDeviceSystemInfo(@PathVariable deviceId: Int): ResponseEntity<Map<String, Any>> {
        val device = repository.findById(deviceId).orElse(null)
            ?: return ResponseEntity.status(404).body(mapOf("error" to "Dispositivo no encontrado"))
        val systemInfo = connectionService.getDeviceSystemInfo(device)
        return ResponseEntity.ok(mapOf("device" to device.toDto(), "systemInfo" to systemInfo))
    }

    @GetMapping("/{deviceId}/resources")
    fun getDeviceResources(@PathVariable deviceId: Int): ResponseEntity<Map<String, Any>> {
        val device = repository.findById(deviceId).orElse(null)
            ?: return ResponseEntity.status(404).body(mapOf("error" to "Dispositivo no encontrado"))
        val resources = connectionService.getDeviceResources(device)
        return ResponseEntity.ok(mapOf("device" to device.toDto(), "resources" to resources))
    }

    @GetMapping("/{deviceId}/info")
    fun getDeviceInfo(@PathVariable deviceId: Int): ResponseEntity<Map<String, Any>> {
        val device = repository.findById(deviceId).orElse(null)
            ?: return ResponseEntity.status(404).body(mapOf("error" to "Dispositivo no encontrado"))
        val interfaces = connectionService.getDeviceInterfaces(device)
        val systemInfo = connectionService.getDeviceSystemInfo(device)
        val resources = connectionService.getDeviceResources(device)
        return ResponseEntity.ok(
            mapOf(
                "device" to device.toDto(),
                "interfaces" to interfaces,
                "systemInfo" to systemInfo,
                "resources" to resources
            )
        )
    }
}
