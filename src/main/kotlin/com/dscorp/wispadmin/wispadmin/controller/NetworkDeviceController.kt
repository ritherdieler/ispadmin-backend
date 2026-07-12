package com.dscorp.wispadmin.wispadmin.controller

import com.dscorp.wispadmin.wispadmin.data.model.NetworkDevice
import com.dscorp.wispadmin.wispadmin.dto.NetworkDeviceDto
import com.dscorp.wispadmin.wispadmin.mapper.toDto
import com.dscorp.wispadmin.wispadmin.repository.NetworkDeviceRepository
import com.dscorp.wispadmin.wispadmin.requestbody.NetworkDeviceRequest
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/networkDevice")
class NetworkDeviceController(
    private val repository: NetworkDeviceRepository
) {

    @PostMapping
    fun registerNetworkDevice(@RequestBody newNetworkDevice: NetworkDeviceRequest): ResponseEntity<NetworkDeviceDto> =
        ResponseEntity.ok(repository.save(newNetworkDevice.toModel()).toDto())

    @GetMapping("genericDevices")
    fun getGenericDevices(): ResponseEntity<List<NetworkDeviceDto>> =
        ResponseEntity.ok(repository.findGenericNetworkDevices().toDto())

    @GetMapping("fiber-and-wireless-devices")
    fun getWirelessAndFiberDevices(): ResponseEntity<List<NetworkDeviceDto>> =
        ResponseEntity.ok(repository.findWirelessAndFiberDevices().toDto())

    @GetMapping
    fun getAll(): ResponseEntity<List<NetworkDeviceDto>> =
        ResponseEntity.ok(repository.findAll().toDto())

    @GetMapping("deviceTypes")
    fun getTypes(): ResponseEntity<List<String>> =
        ResponseEntity.ok(NetworkDevice.NetworkDeviceType.values().map { it.name })

    @GetMapping("coreTypes")
    fun getCoreTypes(): ResponseEntity<List<NetworkDeviceDto>> =
        ResponseEntity.ok(repository.findByNetworkDeviceType(NetworkDevice.NetworkDeviceType.CLOUD_CORE_ROUTER).toDto())
}
