package com.dscorp.wispadmin.traffic.controller

import com.dscorp.wispadmin.traffic.dto.NetworkHourlyProfileDto
import com.dscorp.wispadmin.traffic.dto.NetworkTrafficInsightsDto
import com.dscorp.wispadmin.traffic.dto.NetworkTrafficTrendDto
import com.dscorp.wispadmin.traffic.service.NetworkTrafficAnalyticsService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/traffic/network")
class NetworkTrafficAnalyticsController(
    private val analyticsService: NetworkTrafficAnalyticsService
) {
    @GetMapping("/hourly-profile")
    fun getHourlyProfile(@RequestParam(defaultValue = "3") months: Int): NetworkHourlyProfileDto =
        analyticsService.getHourlyProfile(months)

    @GetMapping("/daily-trend")
    fun getDailyTrend(@RequestParam(defaultValue = "3") months: Int): NetworkTrafficTrendDto =
        analyticsService.getDailyTrend(months)

    @GetMapping("/insights")
    fun getInsights(@RequestParam(defaultValue = "3") months: Int): NetworkTrafficInsightsDto =
        analyticsService.getInsights(months)
}
