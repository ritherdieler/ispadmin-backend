package com.dscorp.wispadmin.acs.controller

import com.dscorp.wispadmin.acs.service.AcsSubscriptionPurgeService
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class AcsSubscriptionPurgeRequest(val sn: String? = null, val deviceId: String? = null)

@RestController
@RequestMapping("/api/acs/v1")
@ConditionalOnProperty(prefix = "gigafiber.subsystems.acs", name = ["enabled"], havingValue = "true", matchIfMissing = true)
@ConditionalOnExpression("'\${acs.datasource.url:}'.trim().length() > 0")
class AcsSubscriptionPurgeController(
    private val purge: AcsSubscriptionPurgeService,
) {
    @PostMapping("/subscription/purge")
    fun purge(@RequestBody request: AcsSubscriptionPurgeRequest): Map<String, String> {
        purge.purge(request.sn, request.deviceId)
        return mapOf("status" to "ok")
    }
}
