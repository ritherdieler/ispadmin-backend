package com.dscorp.wispadmin.observability.controller

import com.dscorp.wispadmin.observability.dto.PagedResponse
import com.dscorp.wispadmin.observability.dto.TraceDetailDto
import com.dscorp.wispadmin.observability.dto.TraceSummaryDto
import com.dscorp.wispadmin.observability.service.ObsTraceQueryService
import org.springframework.beans.factory.annotation.Value
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDateTime
import java.time.ZoneId

@RestController
@RequestMapping("/observability/traces")
class ObservabilityTraceController(
    private val traceQueryService: ObsTraceQueryService,
    @Value("\${app.timezone:America/Lima}") private val appTimezone: String
) {

    @GetMapping
    fun list(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) from: LocalDateTime?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) to: LocalDateTime?,
        @RequestParam(required = false) route: String?,
        @RequestParam(required = false) minDurationMs: Long?,
        @RequestParam(required = false) status: String?,
        @RequestParam(required = false) platform: String?,
        @RequestParam(required = false) sessionId: String?,
        @RequestParam(required = false) release: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "25") size: Int
    ): PagedResponse<TraceSummaryDto> {
        val zone = ZoneId.of(appTimezone)
        return traceQueryService.searchTraces(
            from?.atZone(zone)?.toInstant()?.toEpochMilli(),
            to?.atZone(zone)?.toInstant()?.toEpochMilli(),
            route, minDurationMs, status, platform, sessionId, release, page, size
        )
    }

    @GetMapping("/{traceId}")
    fun detail(@PathVariable traceId: String): ResponseEntity<TraceDetailDto> {
        val trace = traceQueryService.getTrace(traceId) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(trace)
    }
}
