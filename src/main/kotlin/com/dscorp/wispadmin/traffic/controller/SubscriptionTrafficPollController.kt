package com.dscorp.wispadmin.traffic.controller

import com.dscorp.wispadmin.traffic.dto.SubscriptionTrafficPollResultDto
import com.dscorp.wispadmin.traffic.entity.TrafficAggregationRun
import com.dscorp.wispadmin.traffic.service.SubscriptionTrafficPollService
import com.dscorp.wispadmin.traffic.service.TrafficAggregationJobService
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/traffic")
class SubscriptionTrafficPollController(
    private val pollService: SubscriptionTrafficPollService,
    private val aggregationJobService: TrafficAggregationJobService
) {
    @PostMapping("/poll")
    fun pollNow(): SubscriptionTrafficPollResultDto = pollService.pollTraffic()

    @PostMapping("/aggregation/catch-up")
    fun catchUpNow(): Map<String, TrafficAggregationRun> = mapOf(
        "fiveMinute" to aggregationJobService.catchUpFiveMinute(),
        "hourly" to aggregationJobService.catchUpHourly(),
        "daily" to aggregationJobService.catchUpDaily()
    )
}
