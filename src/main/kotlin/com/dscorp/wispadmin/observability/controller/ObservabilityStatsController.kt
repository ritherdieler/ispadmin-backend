package com.dscorp.wispadmin.observability.controller

import com.dscorp.wispadmin.observability.dto.OverviewStatsDto
import com.dscorp.wispadmin.observability.dto.TimeSeriesPointDto
import com.dscorp.wispadmin.observability.service.ObsQueryService
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDateTime

@RestController
@RequestMapping("/observability/stats")
class ObservabilityStatsController(
    private val queryService: ObsQueryService
) {

    @GetMapping("/overview")
    fun overview(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) from: LocalDateTime?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) to: LocalDateTime?
    ): OverviewStatsDto {
        val toDate = to ?: LocalDateTime.now()
        val fromDate = from ?: toDate.minusHours(24)
        return queryService.overview(fromDate, toDate)
    }

    @GetMapping("/events-timeseries")
    fun eventsTimeSeries(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) from: LocalDateTime?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) to: LocalDateTime?
    ): List<TimeSeriesPointDto> {
        val toDate = to ?: LocalDateTime.now()
        val fromDate = from ?: toDate.minusHours(24)
        return queryService.eventsTimeSeries(fromDate, toDate)
    }
}
