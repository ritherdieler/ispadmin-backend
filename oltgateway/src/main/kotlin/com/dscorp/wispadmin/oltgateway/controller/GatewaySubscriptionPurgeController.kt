package com.dscorp.wispadmin.oltgateway.controller

import com.dscorp.wispadmin.oltgateway.service.GatewaySubscriptionPurgeService
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class GatewaySubscriptionPurgeRequest(val sn: String? = null)

@RestController
@RequestMapping("/api/olt-gateway")
class GatewaySubscriptionPurgeController(
    private val purge: GatewaySubscriptionPurgeService,
) {
    @PostMapping("/subscription/purge")
    fun purge(@RequestBody request: GatewaySubscriptionPurgeRequest): Map<String, String> {
        purge.purge(request.sn)
        return mapOf("status" to "ok")
    }
}
