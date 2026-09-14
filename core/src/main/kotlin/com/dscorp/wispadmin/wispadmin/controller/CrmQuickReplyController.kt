package com.dscorp.wispadmin.wispadmin.controller

import com.dscorp.wispadmin.wispadmin.dto.CrmQuickReplyBody
import com.dscorp.wispadmin.wispadmin.security.PlatformAuthFilter
import com.dscorp.wispadmin.wispadmin.service.whatsapp.CrmConversationForbiddenException
import com.dscorp.wispadmin.wispadmin.service.whatsapp.CrmConversationNotFoundException
import com.dscorp.wispadmin.wispadmin.service.whatsapp.CrmConversationValidationException
import com.dscorp.wispadmin.wispadmin.service.whatsapp.CrmQuickReplyService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import javax.servlet.http.HttpServletRequest

@RestController
@RequestMapping("/crm/quick-replies")
class CrmQuickReplyController(
    private val quickReplyService: CrmQuickReplyService
) {

    @GetMapping
    fun list(httpRequest: HttpServletRequest): ResponseEntity<Any> {
        val userId = resolveUserId(httpRequest)
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(mapOf("error" to "Usuario no autenticado"))
        return ResponseEntity.ok(quickReplyService.listForUser(userId))
    }

    @PostMapping
    fun create(
        @RequestBody body: CrmQuickReplyBody,
        httpRequest: HttpServletRequest
    ): ResponseEntity<Any> = mutate(httpRequest) { userId, isAdmin ->
        quickReplyService.create(userId, isAdmin, body)
    }

    @PutMapping("/{id}")
    fun update(
        @PathVariable id: Long,
        @RequestBody body: CrmQuickReplyBody,
        httpRequest: HttpServletRequest
    ): ResponseEntity<Any> = mutate(httpRequest) { userId, isAdmin ->
        quickReplyService.update(id, userId, isAdmin, body)
    }

    @DeleteMapping("/{id}")
    fun delete(
        @PathVariable id: Long,
        httpRequest: HttpServletRequest
    ): ResponseEntity<Any> = mutate(httpRequest) { userId, isAdmin ->
        quickReplyService.delete(id, userId, isAdmin)
        mapOf("ok" to true)
    }

    private fun mutate(
        httpRequest: HttpServletRequest,
        block: (Int, Boolean) -> Any
    ): ResponseEntity<Any> {
        val userId = resolveUserId(httpRequest)
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(mapOf("error" to "Usuario no autenticado"))
        val isAdmin = resolveUserType(httpRequest) == "ADMIN"
        return try {
            ResponseEntity.ok(block(userId, isAdmin))
        } catch (e: CrmConversationNotFoundException) {
            ResponseEntity.status(HttpStatus.NOT_FOUND).body(mapOf("error" to e.message))
        } catch (e: CrmConversationForbiddenException) {
            ResponseEntity.status(HttpStatus.FORBIDDEN).body(mapOf("error" to e.message))
        } catch (e: CrmConversationValidationException) {
            ResponseEntity.badRequest().body(mapOf("error" to e.message))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(mapOf("error" to (e.message ?: "Solicitud invalida")))
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

    private fun resolveUserType(request: HttpServletRequest): String? =
        request.getAttribute(PlatformAuthFilter.AUTH_USER_TYPE_ATTRIBUTE)?.toString()?.trim()?.uppercase()
}
