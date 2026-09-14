package com.dscorp.wispadmin.wispadmin.controller

import com.dscorp.wispadmin.wispadmin.dto.CsatFollowUpUpdateBody
import com.dscorp.wispadmin.wispadmin.security.PlatformAuthFilter
import com.dscorp.wispadmin.wispadmin.service.whatsapp.CsatSurveyService
import com.dscorp.wispadmin.wispadmin.service.whatsapp.WhatsAppAuditService
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.time.LocalDateTime
import javax.servlet.http.HttpServletRequest

@RestController
@RequestMapping("/crm/csat")
class CrmCsatController(
    private val csatSurveyService: CsatSurveyService,
    private val auditService: WhatsAppAuditService
) {

    @GetMapping("/summary")
    fun summary(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate?
    ): ResponseEntity<Any> {
        val range = resolveRange(from, to)
        return ResponseEntity.ok(csatSurveyService.buildSummary(range.first, range.second))
    }

    @GetMapping("/surveys")
    fun surveys(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate?,
        @RequestParam(required = false) status: String?
    ): ResponseEntity<Any> {
        val range = resolveRange(from, to)
        return ResponseEntity.ok(csatSurveyService.listSurveys(range.first, range.second, status))
    }

    @GetMapping("/follow-ups")
    fun followUps(@RequestParam(required = false) status: String?): ResponseEntity<Any> {
        return try {
            ResponseEntity.ok(csatSurveyService.listFollowUps(status))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(mapOf("error" to e.message))
        }
    }

    @PutMapping("/follow-ups/{id}")
    fun updateFollowUp(
        @PathVariable id: Long,
        @RequestBody body: CsatFollowUpUpdateBody,
        httpRequest: HttpServletRequest
    ): ResponseEntity<Any> {
        val username = httpRequest.getAttribute(PlatformAuthFilter.AUTH_USERNAME_ATTRIBUTE) as? String
        return try {
            val updated = csatSurveyService.updateFollowUp(
                id = id,
                status = body.status,
                assignedTo = body.assignedTo,
                actions = body.actions,
                reason = body.reason
            )
            auditService.recordAccess(
                operatorUsername = username,
                resource = "/crm/csat/follow-ups/$id",
                details = "status=${updated.status}"
            )
            ResponseEntity.ok(updated)
        } catch (e: NoSuchElementException) {
            ResponseEntity.notFound().build()
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(mapOf("error" to e.message))
        }
    }

    @PostMapping("/follow-ups/{id}/reopen-ticket")
    fun reopenTicket(
        @PathVariable id: Long,
        httpRequest: HttpServletRequest
    ): ResponseEntity<Any> {
        val username = httpRequest.getAttribute(PlatformAuthFilter.AUTH_USERNAME_ATTRIBUTE) as? String
        return try {
            val ticket = csatSurveyService.reopenTicketFromFollowUp(id)
            auditService.recordAccess(
                operatorUsername = username,
                resource = "/crm/csat/follow-ups/$id/reopen-ticket",
                details = "ticketId=${ticket.id}"
            )
            ResponseEntity.ok(
                mapOf(
                    "ticketId" to ticket.id,
                    "status" to ticket.status.name,
                    "followUpId" to id
                )
            )
        } catch (e: NoSuchElementException) {
            ResponseEntity.notFound().build()
        }
    }

    private fun resolveRange(from: LocalDate?, to: LocalDate?): Pair<LocalDateTime, LocalDateTime> {
        val endDate = to ?: LocalDate.now()
        val startDate = from ?: endDate.minusDays(30)
        return startDate.atStartOfDay() to endDate.plusDays(1).atStartOfDay()
    }
}
