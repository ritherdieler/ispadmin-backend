package com.dscorp.wispadmin.observability.controller

import com.dscorp.wispadmin.observability.dto.ChangeStatusRequest
import com.dscorp.wispadmin.observability.dto.EventDto
import com.dscorp.wispadmin.observability.dto.IssueSummaryDto
import com.dscorp.wispadmin.observability.dto.PagedResponse
import com.dscorp.wispadmin.observability.entity.ObsIssueStatus
import com.dscorp.wispadmin.observability.port.CreateTicketResult
import com.dscorp.wispadmin.observability.service.ObsQueryService
import com.dscorp.wispadmin.observability.service.ObsTicketApplicationService
import com.dscorp.wispadmin.observability.service.TicketOverride
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDateTime

@RestController
@RequestMapping("/observability/issues")
class ObservabilityIssueController(
    private val queryService: ObsQueryService,
    private val ticketApplicationService: ObsTicketApplicationService
) {

    @GetMapping
    fun list(
        @RequestParam(required = false) platform: String?,
        @RequestParam(required = false) severity: String?,
        @RequestParam(required = false) status: ObsIssueStatus?,
        @RequestParam(required = false) environment: String?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) from: LocalDateTime?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) to: LocalDateTime?,
        @RequestParam(required = false) text: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "25") size: Int
    ): PagedResponse<IssueSummaryDto> {
        return queryService.searchIssues(platform, severity, status, environment, from, to, text, page, size)
    }

    @GetMapping("/{id}")
    fun detail(@PathVariable id: Long): ResponseEntity<IssueSummaryDto> {
        val issue = queryService.getIssue(id) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(issue)
    }

    @GetMapping("/{id}/latest-event")
    fun latestEvent(@PathVariable id: Long): ResponseEntity<EventDto> {
        val event = queryService.getLatestEvent(id) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(event)
    }

    @GetMapping("/{id}/occurrences")
    fun occurrences(
        @PathVariable id: Long,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "25") size: Int
    ): PagedResponse<EventDto> {
        return queryService.getOccurrences(id, page, size)
    }

    @PatchMapping("/{id}/status")
    fun changeStatus(
        @PathVariable id: Long,
        @RequestBody body: ChangeStatusRequest
    ): ResponseEntity<IssueSummaryDto> {
        val status = body.status ?: return ResponseEntity.badRequest().build()
        val updated = queryService.changeStatus(id, status) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(updated)
    }

    @PostMapping("/{id}/ticket")
    fun createTicket(
        @PathVariable id: Long,
        @RequestBody(required = false) override: TicketOverride?
    ): ResponseEntity<CreateTicketResult> {
        val result = ticketApplicationService.createTicketForIssue(id, override)
        return if (result.ok) ResponseEntity.ok(result)
        else ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(result)
    }

    @PostMapping("/{id}/jira")
    fun createJira(
        @PathVariable id: Long,
        @RequestBody(required = false) override: TicketOverride?
    ): ResponseEntity<CreateTicketResult> {
        val result = ticketApplicationService.createTicketForIssue(id, override)
        return if (result.ok) ResponseEntity.ok(result)
        else ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(result)
    }

    @DeleteMapping("/{id}/ticket")
    fun unlinkTicket(@PathVariable id: Long): ResponseEntity<Void> {
        val ok = ticketApplicationService.unlink(id)
        return if (ok) ResponseEntity.noContent().build() else ResponseEntity.notFound().build()
    }
}
