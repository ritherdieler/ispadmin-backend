package com.dscorp.wispadmin.wispadmin.controller

import com.dscorp.wispadmin.wispadmin.service.whatsapp.CrmMetricsService
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.time.LocalDateTime

@RestController
@RequestMapping("/crm/metrics")
class CrmMetricsController(
    private val metricsService: CrmMetricsService
) {

    @GetMapping("/summary")
    fun summary(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate?
    ): ResponseEntity<Any> {
        val range = resolveRange(from, to)
        return ResponseEntity.ok(metricsService.buildSummary(range.first, range.second))
    }

    @GetMapping("/agents")
    fun agents(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate?
    ): ResponseEntity<Any> {
        val range = resolveRange(from, to)
        return ResponseEntity.ok(metricsService.buildAgentBreakdown(range.first, range.second))
    }

    @GetMapping("/shift-handoff")
    fun shiftHandoff(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) from: LocalDateTime?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) to: LocalDateTime?
    ): ResponseEntity<Any> {
        val end = to ?: LocalDateTime.now()
        val start = from ?: end.minusHours(8)
        return ResponseEntity.ok(metricsService.buildShiftHandoff(start, end))
    }

    private fun resolveRange(from: LocalDate?, to: LocalDate?): Pair<LocalDateTime, LocalDateTime> {
        val endDate = to ?: LocalDate.now()
        val startDate = from ?: endDate.minusDays(30)
        return startDate.atStartOfDay() to endDate.plusDays(1).atStartOfDay()
    }
}
