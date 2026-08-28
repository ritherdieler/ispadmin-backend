package com.dscorp.wispadmin.traffic.controller

import com.dscorp.wispadmin.traffic.dto.SubscriptionTrafficPollResultDto
import com.dscorp.wispadmin.traffic.service.SubscriptionTrafficPollService
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/traffic")
class SubscriptionTrafficPollController(
    private val pollService: SubscriptionTrafficPollService
) {
    @PostMapping("/poll")
    fun pollNow(): SubscriptionTrafficPollResultDto = pollService.pollTraffic()
}
