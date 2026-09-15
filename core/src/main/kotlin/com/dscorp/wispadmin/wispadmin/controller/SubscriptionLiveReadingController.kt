package com.dscorp.wispadmin.wispadmin.controller

import com.dscorp.wispadmin.wispadmin.dto.SubscriptionLiveReadingDto
import com.dscorp.wispadmin.wispadmin.service.SubscriptionLiveReadingService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/subscription")
class SubscriptionLiveReadingController(
    private val liveReadingService: SubscriptionLiveReadingService,
) {
    @GetMapping("/{subscriptionId}/live-readings")
    fun liveReadings(@PathVariable subscriptionId: Int): SubscriptionLiveReadingDto =
        liveReadingService.read(subscriptionId)
}
