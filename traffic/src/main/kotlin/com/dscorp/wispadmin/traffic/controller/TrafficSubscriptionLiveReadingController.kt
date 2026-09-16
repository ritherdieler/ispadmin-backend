package com.dscorp.wispadmin.traffic.controller

import com.dscorp.wispadmin.traffic.dto.SubscriptionLiveReadingDto
import com.dscorp.wispadmin.traffic.service.LiveReadingIdentity
import com.dscorp.wispadmin.traffic.service.SubscriptionLiveReadingService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/traffic/v1")
class TrafficSubscriptionLiveReadingController(
    private val liveReadingService: SubscriptionLiveReadingService,
) {
    @GetMapping("/by-subscription/{id}/live-readings")
    fun liveReadings(
        @PathVariable id: Int,
        @RequestParam(required = false) accessMode: String?,
        @RequestParam(required = false) ip: String?,
        @RequestParam(required = false) pppoeLastIp: String?,
        @RequestParam(required = false) pppoeUsername: String?,
        @RequestParam(required = false) hostDeviceId: Int?,
        @RequestParam(required = false) envTag: String?,
    ): SubscriptionLiveReadingDto {
        return liveReadingService.read(
            LiveReadingIdentity(
                subscriptionId = id,
                accessMode = accessMode.orEmpty(),
                ip = ip,
                pppoeLastIp = pppoeLastIp,
                pppoeUsername = pppoeUsername,
                hostDeviceId = hostDeviceId,
                envTag = envTag,
            ),
        )
    }
}
