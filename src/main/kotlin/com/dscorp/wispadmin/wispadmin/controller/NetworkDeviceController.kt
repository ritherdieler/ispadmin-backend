package com.dscorp.wispadmin.wispadmin.controller

import com.dscorp.wispadmin.wispadmin.data.model.NetworkDevice
import com.dscorp.wispadmin.wispadmin.dto.NetworkDeviceDto
import com.dscorp.wispadmin.wispadmin.mapper.toDto
import com.dscorp.wispadmin.wispadmin.repository.NetworkDeviceRepository
import com.dscorp.wispadmin.wispadmin.requestbody.NetworkDeviceRequest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.lang.Exception

@RestController
@RequestMapping("/networkDevice")
class NetworkDeviceController {
    val listObjectErrorResponse: ResponseEntity<List<NetworkDeviceDto>> = ResponseEntity.status(500).body(null)

    val objectErrorResponse: ResponseEntity<NetworkDeviceDto> = ResponseEntity.status(500).body(null)

    @Autowired
    lateinit var repository: NetworkDeviceRepository

    @PostMapping
    fun registerNetworkDevice(@RequestBody newNetworkDevice: NetworkDeviceRequest): ResponseEntity<NetworkDeviceDto> {
        return try {
            val savedNetworkDevice = repository.save(newNetworkDevice.toModel())
            ResponseEntity.status(200).body(savedNetworkDevice.toDto())
        } catch (e: Exception) {
            e.printStackTrace()
            objectErrorResponse
        }
    }

    @GetMapping("genericDevices")
    fun getGenericDevices(): ResponseEntity<List<NetworkDeviceDto>> {
        return try {
            val networkDevices = repository.findGenericNetworkDevices()
            ResponseEntity.status(200).body(networkDevices.toDto())
        } catch (e: Exception) {
            e.printStackTrace()
            listObjectErrorResponse
        }
    }

    @GetMapping("fiber-and-wireless-devices")
    fun getWirelessAndFiberDevices(): ResponseEntity<List<NetworkDeviceDto>> {
        return try {
            val networkDevices = repository.findWirelessAndFiberDevices()
            ResponseEntity.status(200).body(networkDevices.toDto())
        } catch (e: Exception) {
            e.printStackTrace()
            listObjectErrorResponse
        }
    }


    @GetMapping
    fun getAll(): ResponseEntity<List<NetworkDeviceDto>> {
        return try {
            val networkDevices = repository.findAll()
            ResponseEntity.status(200).body(networkDevices.toDto())
        } catch (e: Exception) {
            e.printStackTrace()
            listObjectErrorResponse
        }
    }

    @GetMapping("deviceTypes")
    fun getTypes(): ResponseEntity<List<String>> {
        return try {
            val types = NetworkDevice.NetworkDeviceType.values().map { it.name }
            ResponseEntity.status(200).body(types)
        } catch (e: Exception) {
            e.printStackTrace()
            ResponseEntity.status(500).body(null)
        }
    }

    @GetMapping("coreTypes")
    fun getCoreTypes(): ResponseEntity<List<NetworkDeviceDto>> {
        return try {
            val types = repository.findByNetworkDeviceType(NetworkDevice.NetworkDeviceType.CLOUD_CORE_ROUTER).toDto()
            ResponseEntity.status(200).body(types)
        } catch (e: Exception) {
            e.printStackTrace()
            ResponseEntity.status(500).body(null)
        }
    }

}
