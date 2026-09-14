package com.dscorp.wispadmin.wispadmin.controller

import com.dscorp.wispadmin.wispadmin.dto.CrmCreateTicketFromConversationBody
import com.dscorp.wispadmin.wispadmin.dto.CrmCreateTicketResultDto
import com.dscorp.wispadmin.wispadmin.dto.CrmTicketDto
import com.dscorp.wispadmin.wispadmin.security.PlatformAuthFilter
import com.dscorp.wispadmin.wispadmin.service.whatsapp.CrmConversationNotFoundException
import com.dscorp.wispadmin.wispadmin.service.whatsapp.CrmTicketLinkService
import com.dscorp.wispadmin.wispadmin.service.whatsapp.WhatsAppAuditService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import javax.servlet.http.HttpServletRequest

@RestController
@RequestMapping("/crm")
class CrmTicketController(
    private val crmTicketLinkService: CrmTicketLinkService,
    private val auditService: WhatsAppAuditService
) {

    @GetMapping("/conversations/{id}/tickets")
    fun listForConversation(@PathVariable id: Long): ResponseEntity<List<CrmTicketDto>> {
        return ResponseEntity.ok(crmTicketLinkService.listTicketsForConversation(id))
    }

    @PostMapping("/conversations/{id}/tickets")
    fun createFromConversation(
        @PathVariable id: Long,
        @RequestBody body: CrmCreateTicketFromConversationBody,
        httpRequest: HttpServletRequest
    ): ResponseEntity<Any> {
        val username = httpRequest.getAttribute(PlatformAuthFilter.AUTH_USERNAME_ATTRIBUTE) as? String
        return try {
            val result: CrmCreateTicketResultDto = crmTicketLinkService.createTicketFromConversation(
                conversationId = id,
                category = body.category,
                description = body.description,
                createdBy = username,
                customerName = body.customerName
            )
            auditService.recordAccess(
                operatorUsername = username,
                resource = "/crm/conversations/$id/tickets",
                details = "ticketId=${result.ticket.id};dedup=${result.deduplicated}"
            )
            ResponseEntity.status(if (result.deduplicated) HttpStatus.OK else HttpStatus.CREATED).body(result)
        } catch (e: CrmConversationNotFoundException) {
            ResponseEntity.status(HttpStatus.NOT_FOUND).body(mapOf("error" to e.message))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(mapOf("error" to e.message))
        }
    }

    @GetMapping("/tickets/by-phone/{phone}")
    fun listForPhone(@PathVariable phone: String): ResponseEntity<List<CrmTicketDto>> {
        return ResponseEntity.ok(crmTicketLinkService.listTicketsForPhone(phone))
    }

    @GetMapping("/tickets/{ticketId}/conversation")
    fun conversationForTicket(@PathVariable ticketId: Int): ResponseEntity<Any> {
        val conversationId = crmTicketLinkService.getConversationIdForTicket(ticketId)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(mapOf("ticketId" to ticketId, "conversationId" to conversationId))
    }
}
