package com.dscorp.wispadmin.observability.controller

import com.dscorp.wispadmin.observability.dto.EndpointDetailDto
import com.dscorp.wispadmin.observability.dto.EndpointMetricAggregateDto
import com.dscorp.wispadmin.observability.dto.EndpointMetricPointDto
import com.dscorp.wispadmin.observability.dto.RumMetricAggregateDto
import com.dscorp.wispadmin.observability.dto.RumMetricPointDto
import com.dscorp.wispadmin.observability.dto.SystemMetricPointDto
import com.dscorp.wispadmin.observability.service.ObsEndpointDetailService
import com.dscorp.wispadmin.observability.service.ObsMetricQueryService
import com.dscorp.wispadmin.observability.service.ObsRumQueryService
import com.dscorp.wispadmin.observability.service.ObsSystemMetricQueryService
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDateTime

@RestController
@RequestMapping("/observability/metrics")
class ObservabilityMetricController(
    private val metricQueryService: ObsMetricQueryService,
    private val rumMetricQueryService: ObsRumQueryService,
    private val systemMetricQueryService: ObsSystemMetricQueryService,
    private val endpointDetailService: ObsEndpointDetailService
) {

    @GetMapping("/endpoints")
    fun endpoints(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) from: LocalDateTime?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) to: LocalDateTime?,
        @RequestParam(required = false) release: String?
    ): List<EndpointMetricAggregateDto> {
        val toDate = to ?: LocalDateTime.now()
        val fromDate = from ?: toDate.minusHours(1)
        return metricQueryService.aggregate(fromDate, toDate, release)
    }

    @GetMapping("/endpoints/detail")
    fun endpointDetail(
        @RequestParam route: String,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) from: LocalDateTime?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) to: LocalDateTime?,
        @RequestParam(required = false) release: String?
    ): EndpointDetailDto {
        val toDate = to ?: LocalDateTime.now()
        val fromDate = from ?: toDate.minusHours(1)
        return endpointDetailService.detail(route, fromDate, toDate, release)
    }

    @GetMapping("/timeseries")
    fun timeSeries(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) from: LocalDateTime?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) to: LocalDateTime?,
        @RequestParam(required = false) route: String?
    ): List<EndpointMetricPointDto> {
        val toDate = to ?: LocalDateTime.now()
        val fromDate = from ?: toDate.minusHours(1)
        return metricQueryService.timeSeries(fromDate, toDate, route)
    }

    @GetMapping("/web-vitals")
    fun webVitals(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) from: LocalDateTime?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) to: LocalDateTime?,
        @RequestParam(required = false) release: String?
    ): List<RumMetricAggregateDto> {
        val toDate = to ?: LocalDateTime.now()
        val fromDate = from ?: toDate.minusHours(1)
        return rumMetricQueryService.aggregate(fromDate, toDate, release)
    }

    @GetMapping("/web-vitals/timeseries")
    fun webVitalsTimeSeries(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) from: LocalDateTime?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) to: LocalDateTime?,
        @RequestParam(required = false) page: String?,
        @RequestParam(required = false) metric: String?,
        @RequestParam(required = false) release: String?
    ): List<RumMetricPointDto> {
        val toDate = to ?: LocalDateTime.now()
        val fromDate = from ?: toDate.minusHours(1)
        return rumMetricQueryService.timeSeries(fromDate, toDate, page, metric, release)
    }

    @GetMapping("/system")
    fun system(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) from: LocalDateTime?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) to: LocalDateTime?
    ): List<SystemMetricPointDto> {
        val toDate = to ?: LocalDateTime.now()
        val fromDate = from ?: toDate.minusHours(1)
        return systemMetricQueryService.timeSeries(fromDate, toDate)
    }

    @GetMapping("/system/latest")
    fun systemLatest(): SystemMetricPointDto? {
        return systemMetricQueryService.latest()
    }
}
