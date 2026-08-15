package com.dscorp.wispadmin.wispadmin.controller

import com.dscorp.wispadmin.wispadmin.dto.WhatsAppQuickReplyBody
import com.dscorp.wispadmin.wispadmin.security.CrmAccessPolicy
import com.dscorp.wispadmin.wispadmin.security.PlatformAuthFilter
import com.dscorp.wispadmin.wispadmin.service.whatsapp.CrmConversationForbiddenException
import com.dscorp.wispadmin.wispadmin.service.whatsapp.CrmConversationNotFoundException
import com.dscorp.wispadmin.wispadmin.service.whatsapp.CrmConversationValidationException
import com.dscorp.wispadmin.wispadmin.service.whatsapp.WhatsAppQuickReplyService
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
@RequestMapping("/whatsapp/quick-replies")
class WhatsAppQuickReplyController(
    private val quickReplyService: WhatsAppQuickReplyService
) {
    @GetMapping
    fun list(httpRequest: HttpServletRequest): ResponseEntity<Any> {
        if (resolveUserId(httpRequest) == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(mapOf("error" to "Usuario no autenticado"))
        }
        return ResponseEntity.ok(quickReplyService.list())
    }

    @PostMapping
    fun create(
        @RequestBody body: WhatsAppQuickReplyBody,
        httpRequest: HttpServletRequest
    ): ResponseEntity<Any> = mutate(httpRequest) {
        quickReplyService.create(body)
    }

    @PutMapping("/{id}")
    fun update(
        @PathVariable id: Long,
        @RequestBody body: WhatsAppQuickReplyBody,
        httpRequest: HttpServletRequest
    ): ResponseEntity<Any> = mutate(httpRequest) {
        quickReplyService.update(id, body)
    }

    @DeleteMapping("/{id}")
    fun delete(
        @PathVariable id: Long,
        httpRequest: HttpServletRequest
    ): ResponseEntity<Any> = mutate(httpRequest) {
        quickReplyService.delete(id)
        mapOf("ok" to true)
    }

    private fun mutate(
        httpRequest: HttpServletRequest,
        block: () -> Any
    ): ResponseEntity<Any> {
        if (resolveUserId(httpRequest) == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(mapOf("error" to "Usuario no autenticado"))
        }
        if (!CrmAccessPolicy.canManageCrmSecrets(resolveUserType(httpRequest))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(mapOf("error" to "Solo ADMIN puede modificar respuestas rapidas"))
        }
        return try {
            ResponseEntity.ok(block())
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
        request.getAttribute(PlatformAuthFilter.AUTH_USER_TYPE_ATTRIBUTE)?.toString()
}
