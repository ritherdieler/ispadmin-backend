package com.dscorp.wispadmin.traffic.controller

import com.dscorp.wispadmin.traffic.dto.SubscriptionTrafficDayDto
import com.dscorp.wispadmin.traffic.dto.SubscriptionTrafficLatestDto
import com.dscorp.wispadmin.traffic.dto.SubscriptionTrafficSeriesDto
import com.dscorp.wispadmin.traffic.dto.SubscriptionTrafficSummaryDto
import com.dscorp.wispadmin.traffic.service.SubscriptionTrafficQueryService
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDate
import java.time.LocalDateTime

@RestController
@RequestMapping("/subscription")
class SubscriptionTrafficController(
    private val queryService: SubscriptionTrafficQueryService
) {

    @GetMapping("/{id}/traffic")
    fun getTrafficSeries(
        @PathVariable id: Int,
        @RequestParam(defaultValue = "sample") granularity: String,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) from: LocalDateTime?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) to: LocalDateTime?
    ): SubscriptionTrafficSeriesDto {
        return queryService.getSeries(id, granularity, from, to)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Subscription not found")
    }

    @GetMapping("/{id}/traffic/latest")
    fun getTrafficLatest(@PathVariable id: Int): SubscriptionTrafficLatestDto {
        return queryService.getLatest(id)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Subscription not found")
    }

    @GetMapping("/{id}/traffic/summary")
    fun getTrafficSummary(
        @PathVariable id: Int,
        @RequestParam(required = false) month: String?
    ): SubscriptionTrafficSummaryDto {
        return queryService.getSummary(id, month)
    }

    @GetMapping("/{id}/traffic/today")
    fun getTrafficToday(@PathVariable id: Int): SubscriptionTrafficDayDto {
        return queryService.getToday(id)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Subscription not found")
    }

    @GetMapping("/{id}/traffic/day")
    fun getTrafficDay(
        @PathVariable id: Int,
        @RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) date: LocalDate
    ): SubscriptionTrafficDayDto {
        return queryService.getDay(id, date)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Subscription not found")
    }
}
