package com.dscorp.wispadmin.wispadmin.trafficclient

import com.dscorp.wispadmin.wispadmin.data.model.NetworkDevice
import com.dscorp.wispadmin.wispadmin.repository.NetworkDeviceRepository
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/internal/traffic/routers")
class TrafficRouterDirectoryController(private val routers: NetworkDeviceRepository) {
    @GetMapping
    fun list(@RequestParam(defaultValue="0") page: Int, @RequestParam(defaultValue="200") size: Int): RouterDirectoryPage {
        require(page >= 0 && size in 1..200) { "Invalid router page" }
        val result = routers.findAll(PageRequest.of(page, size, Sort.by("id")))
        return RouterDirectoryPage(result.content.filter { it.networkDeviceType != NetworkDevice.NetworkDeviceType.GENERIC }
            .map { RouterDirectoryItem(it.id, it.name ?: "router-${it.id}", it.ipAddress.orEmpty(), it.username.orEmpty(), it.password.orEmpty(), !it.disabled && !it.ipAddress.isNullOrBlank()) }, result.totalPages)
    }
}
data class RouterDirectoryPage(val items: List<RouterDirectoryItem>, val totalPages: Int)
class RouterDirectoryItem(val id: Int, val name: String, val host: String, val username: String, val password: String, val enabled: Boolean)
