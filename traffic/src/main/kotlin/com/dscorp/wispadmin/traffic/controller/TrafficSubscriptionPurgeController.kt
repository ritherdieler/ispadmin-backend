package com.dscorp.wispadmin.traffic.controller

import com.dscorp.wispadmin.traffic.service.TrafficSubscriptionPurgeService
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class TrafficSubscriptionPurgeRequest(
    val subscriptionId: Int = 0,
    val ip: String? = null,
    val pppoeUsername: String? = null,
)

@RestController
@RequestMapping("/api/traffic/v1")
class TrafficSubscriptionPurgeController(
    private val purge: TrafficSubscriptionPurgeService,
) {
    @PostMapping("/subscription/purge")
    fun purge(@RequestBody request: TrafficSubscriptionPurgeRequest): Map<String, String> {
        purge.purge(request.subscriptionId, request.ip, request.pppoeUsername)
        return mapOf("status" to "ok")
    }
}
