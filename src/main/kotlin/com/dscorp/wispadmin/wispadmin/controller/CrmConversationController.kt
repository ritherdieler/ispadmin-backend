package com.dscorp.wispadmin.wispadmin.controller

import com.dscorp.wispadmin.wispadmin.data.model.CrmConversationStatus
import com.dscorp.wispadmin.wispadmin.dto.CrmConversationDto
import com.dscorp.wispadmin.wispadmin.dto.CrmBotControlBody
import com.dscorp.wispadmin.wispadmin.dto.CrmInternalNoteBody
import com.dscorp.wispadmin.wispadmin.dto.CrmReleaseBody
import com.dscorp.wispadmin.wispadmin.dto.CrmReopenBody
import com.dscorp.wispadmin.wispadmin.dto.CrmResolveBody
import com.dscorp.wispadmin.wispadmin.dto.CrmTransferBody
import com.dscorp.wispadmin.wispadmin.security.PlatformAuthFilter
import com.dscorp.wispadmin.wispadmin.service.whatsapp.CrmConversationConflictException
import com.dscorp.wispadmin.wispadmin.service.whatsapp.CrmConversationFilter
import com.dscorp.wispadmin.wispadmin.service.whatsapp.CrmConversationForbiddenException
import com.dscorp.wispadmin.wispadmin.service.whatsapp.CrmConversationNotFoundException
import com.dscorp.wispadmin.wispadmin.service.whatsapp.CrmConversationService
import com.dscorp.wispadmin.wispadmin.service.whatsapp.CrmConversationValidationException
import com.dscorp.wispadmin.wispadmin.service.whatsapp.WhatsAppAuditService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import javax.servlet.http.HttpServletRequest

@RestController
@RequestMapping("/crm/conversations")
class CrmConversationController(
    private val crmConversationService: CrmConversationService,
    private val auditService: WhatsAppAuditService
) {

    @GetMapping
    fun list(
        @RequestParam(required = false) status: String?,
        @RequestParam(required = false) assignedAgentId: Int?,
        @RequestParam(required = false) priority: Int?,
        @RequestParam(required = false) search: String?,
        @RequestParam(required = false) limit: Int?
    ): ResponseEntity<List<CrmConversationDto>> {
        val parsedStatus = status?.trim()?.takeIf { it.isNotEmpty() }?.let {
            runCatching { CrmConversationStatus.valueOf(it.uppercase()) }.getOrElse {
                return ResponseEntity.badRequest().build()
            }
        }
        return ResponseEntity.ok(
            crmConversationService.list(
                CrmConversationFilter(
                    status = parsedStatus,
                    assignedAgentId = assignedAgentId,
                    priority = priority,
                    search = search,
                    limit = limit ?: 100
                )
            )
        )
    }

    @GetMapping("/{id}")
    fun getById(@PathVariable id: Long): ResponseEntity<Any> {
        return try {
            ResponseEntity.ok(crmConversationService.getById(id))
        } catch (e: CrmConversationNotFoundException) {
            ResponseEntity.status(HttpStatus.NOT_FOUND).body(mapOf("error" to e.message))
        }
    }

    @GetMapping("/by-phone/{phone}")
    fun getByPhone(@PathVariable phone: String): ResponseEntity<CrmConversationDto> {
        val dto = crmConversationService.getByPhone(phone) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(dto)
    }

    @PostMapping("/{id}/claim")
    fun claim(@PathVariable id: Long, httpRequest: HttpServletRequest): ResponseEntity<Any> {
        return executeMutation(httpRequest) { agentId, username, _ ->
            val result = crmConversationService.claim(id, agentId, username)
            auditService.recordAccess(
                operatorUsername = username,
                resource = "/crm/conversations/$id/claim",
                details = "phone=${result.phone}"
            )
            result
        }
    }

    @PostMapping("/{id}/release")
    fun release(
        @PathVariable id: Long,
        @RequestBody(required = false) body: CrmReleaseBody?,
        httpRequest: HttpServletRequest
    ): ResponseEntity<Any> {
        return executeMutation(httpRequest) { agentId, username, isAdmin ->
            val result = crmConversationService.release(
                conversationId = id,
                agentId = agentId,
                operatorUsername = username,
                isAdmin = isAdmin,
                note = body?.note
            )
            auditService.recordAccess(
                operatorUsername = username,
                resource = "/crm/conversations/$id/release",
                details = "phone=${result.phone}"
            )
            result
        }
    }

    @PostMapping("/{id}/transfer")
    fun transfer(
        @PathVariable id: Long,
        @RequestBody body: CrmTransferBody,
        httpRequest: HttpServletRequest
    ): ResponseEntity<Any> {
        return executeMutation(httpRequest) { agentId, username, isAdmin ->
            val result = crmConversationService.transfer(
                conversationId = id,
                fromAgentId = agentId,
                toAgentId = body.toAgentId,
                note = body.note,
                operatorUsername = username,
                isAdmin = isAdmin
            )
            auditService.recordAccess(
                operatorUsername = username,
                resource = "/crm/conversations/$id/transfer",
                details = "to=${body.toAgentId}"
            )
            result
        }
    }

    @PostMapping("/{id}/resolve")
    fun resolve(
        @PathVariable id: Long,
        @RequestBody(required = false) body: CrmResolveBody?,
        httpRequest: HttpServletRequest
    ): ResponseEntity<Any> {
        return executeMutation(httpRequest) { agentId, username, isAdmin ->
            val result = crmConversationService.resolve(
                conversationId = id,
                agentId = agentId,
                operatorUsername = username,
                isAdmin = isAdmin,
                resumeBot = body?.resumeBot ?: true,
                note = body?.note
            )
            auditService.recordAccess(
                operatorUsername = username,
                resource = "/crm/conversations/$id/resolve",
                details = "phone=${result.phone}"
            )
            result
        }
    }

    @PostMapping("/{id}/reopen")
    fun reopen(
        @PathVariable id: Long,
        @RequestBody(required = false) body: CrmReopenBody?,
        httpRequest: HttpServletRequest
    ): ResponseEntity<Any> {
        return executeMutation(httpRequest) { agentId, username, isAdmin ->
            val result = crmConversationService.reopen(
                conversationId = id,
                agentId = agentId,
                operatorUsername = username,
                isAdmin = isAdmin,
                note = body?.note
            )
            auditService.recordAccess(
                operatorUsername = username,
                resource = "/crm/conversations/$id/reopen",
                details = "phone=${result.phone}"
            )
            result
        }
    }

    @GetMapping("/{id}/assignments")
    fun assignments(@PathVariable id: Long): ResponseEntity<Any> {
        return try {
            ResponseEntity.ok(crmConversationService.listAssignmentHistory(id))
        } catch (e: CrmConversationNotFoundException) {
            ResponseEntity.status(HttpStatus.NOT_FOUND).body(mapOf("error" to e.message))
        }
    }

    @GetMapping("/{id}/notes")
    fun notes(@PathVariable id: Long): ResponseEntity<Any> {
        return try {
            ResponseEntity.ok(crmConversationService.listInternalNotes(id))
        } catch (e: CrmConversationNotFoundException) {
            ResponseEntity.status(HttpStatus.NOT_FOUND).body(mapOf("error" to e.message))
        }
    }

    @PostMapping("/{id}/notes")
    fun addNote(
        @PathVariable id: Long,
        @RequestBody body: CrmInternalNoteBody,
        httpRequest: HttpServletRequest
    ): ResponseEntity<Any> {
        return executeMutation(httpRequest) { agentId, username, _ ->
            val note = crmConversationService.addInternalNote(
                conversationId = id,
                authorId = agentId,
                text = body.text,
                operatorUsername = username
            )
            auditService.recordAccess(
                operatorUsername = username,
                resource = "/crm/conversations/$id/notes",
                details = "noteId=${note.id}"
            )
            note
        }
    }

    @PostMapping("/{id}/pause-bot")
    fun pauseBot(
        @PathVariable id: Long,
        @RequestBody(required = false) body: CrmBotControlBody?,
        httpRequest: HttpServletRequest
    ): ResponseEntity<Any> {
        return executeMutation(httpRequest) { agentId, username, isAdmin ->
            val result = crmConversationService.pauseBot(id, agentId, username, isAdmin, body?.note)
            auditService.recordAccess(username, "/crm/conversations/$id/pause-bot", "phone=${result.phone}")
            result
        }
    }

    @PostMapping("/{id}/resume-bot")
    fun resumeBot(
        @PathVariable id: Long,
        @RequestBody(required = false) body: CrmBotControlBody?,
        httpRequest: HttpServletRequest
    ): ResponseEntity<Any> {
        return executeMutation(httpRequest) { agentId, username, isAdmin ->
            val result = crmConversationService.resumeBot(id, agentId, username, isAdmin, body?.note)
            auditService.recordAccess(username, "/crm/conversations/$id/resume-bot", "phone=${result.phone}")
            result
        }
    }

    @PostMapping("/{id}/suggest-reply")
    fun suggestReply(@PathVariable id: Long, httpRequest: HttpServletRequest): ResponseEntity<Any> {
        val username = resolveUsername(httpRequest)
        return try {
            val suggestion = crmConversationService.suggestReply(id)
            auditService.recordAccess(username, "/crm/conversations/$id/suggest-reply", "source=${suggestion.source}")
            ResponseEntity.ok(suggestion)
        } catch (e: CrmConversationNotFoundException) {
            ResponseEntity.status(HttpStatus.NOT_FOUND).body(mapOf("error" to e.message))
        }
    }

    private fun executeMutation(
        request: HttpServletRequest,
        block: (agentId: Int, username: String?, isAdmin: Boolean) -> Any
    ): ResponseEntity<Any> {
        val agentId = resolveUserId(request)
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(mapOf("error" to "Usuario no autenticado"))
        val username = resolveUsername(request)
        val isAdmin = resolveUserType(request) == "ADMIN"
        return try {
            ResponseEntity.ok(block(agentId, username, isAdmin))
        } catch (e: CrmConversationNotFoundException) {
            ResponseEntity.status(HttpStatus.NOT_FOUND).body(mapOf("error" to e.message))
        } catch (e: CrmConversationConflictException) {
            ResponseEntity.status(HttpStatus.CONFLICT).body(mapOf("error" to e.message))
        } catch (e: CrmConversationForbiddenException) {
            ResponseEntity.status(HttpStatus.FORBIDDEN).body(mapOf("error" to e.message))
        } catch (e: CrmConversationValidationException) {
            ResponseEntity.badRequest().body(mapOf("error" to e.message))
        }
    }

    private fun resolveUserId(request: HttpServletRequest): Int? {
        val raw = request.getAttribute(PlatformAuthFilter.AUTH_USER_ID_ATTRIBUTE) ?: return null
        return when (raw) {
            is Int -> raw
            is Number -> raw.toInt()
            else -> raw.toString().toIntOrNull()
        }
    }

    private fun resolveUsername(request: HttpServletRequest): String? =
        request.getAttribute(PlatformAuthFilter.AUTH_USERNAME_ATTRIBUTE)?.toString()

    private fun resolveUserType(request: HttpServletRequest): String? =
        request.getAttribute(PlatformAuthFilter.AUTH_USER_TYPE_ATTRIBUTE)?.toString()?.trim()?.uppercase()
}
