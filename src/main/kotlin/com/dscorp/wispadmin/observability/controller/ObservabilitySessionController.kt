package com.dscorp.wispadmin.observability.controller

import com.dscorp.wispadmin.observability.dto.PagedResponse
import com.dscorp.wispadmin.observability.dto.SessionDetailDto
import com.dscorp.wispadmin.observability.dto.SessionSummaryDto
import com.dscorp.wispadmin.observability.service.ObsSessionQueryService
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDateTime

@RestController
@RequestMapping("/observability/sessions")
class ObservabilitySessionController(
    private val sessionQueryService: ObsSessionQueryService
) {

    @GetMapping
    fun list(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) from: LocalDateTime?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) to: LocalDateTime?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "25") size: Int
    ): PagedResponse<SessionSummaryDto> {
        val toDate = to ?: LocalDateTime.now()
        val fromDate = from ?: toDate.minusHours(24)
        return sessionQueryService.listSessions(fromDate, toDate, page, size)
    }

    @GetMapping("/{sessionId}")
    fun detail(@PathVariable sessionId: String): ResponseEntity<SessionDetailDto> {
        val session = sessionQueryService.getSession(sessionId) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(session)
    }
}
