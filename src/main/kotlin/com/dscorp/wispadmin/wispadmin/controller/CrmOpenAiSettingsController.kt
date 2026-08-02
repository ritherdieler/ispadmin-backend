package com.dscorp.wispadmin.wispadmin.controller

import com.dscorp.wispadmin.wispadmin.dto.CrmOpenAiSettingsDto
import com.dscorp.wispadmin.wispadmin.dto.CrmOpenAiSettingsUpdateBody
import com.dscorp.wispadmin.wispadmin.dto.CrmOpenAiTestResultDto
import com.dscorp.wispadmin.wispadmin.security.CrmAccessPolicy
import com.dscorp.wispadmin.wispadmin.security.PlatformAuthFilter
import com.dscorp.wispadmin.wispadmin.service.whatsapp.CrmOpenAiSettingsService
import com.dscorp.wispadmin.wispadmin.service.whatsapp.LlmClient
import com.dscorp.wispadmin.wispadmin.service.whatsapp.WhatsAppAuditService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import javax.servlet.http.HttpServletRequest

@RestController
@RequestMapping("/crm/settings/openai")
class CrmOpenAiSettingsController(
    private val settingsService: CrmOpenAiSettingsService,
    private val llmClient: LlmClient,
    private val auditService: WhatsAppAuditService
) {

    @GetMapping
    fun get(httpRequest: HttpServletRequest): ResponseEntity<Any> {
        if (!isAdmin(httpRequest)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(mapOf("error" to "Solo ADMIN puede ver la configuracion OpenAI"))
        }
        return ResponseEntity.ok(settingsService.getSettings())
    }

    @PutMapping
    fun put(
        @RequestBody body: CrmOpenAiSettingsUpdateBody,
        httpRequest: HttpServletRequest
    ): ResponseEntity<Any> {
        if (!isAdmin(httpRequest)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(mapOf("error" to "Solo ADMIN puede editar la configuracion OpenAI"))
        }
        val username = resolveUsername(httpRequest)
        val result: CrmOpenAiSettingsDto = settingsService.updateSettings(
            apiKey = body.apiKey,
            model = body.model,
            enabled = body.enabled,
            updatedBy = username
        )
        auditService.recordAccess(
            operatorUsername = username,
            resource = "/crm/settings/openai",
            details = "configured=${result.configured};enabled=${result.enabled};model=${result.model};masked=${result.maskedApiKey}"
        )
        return ResponseEntity.ok(result)
    }

    @PostMapping("/test")
    fun test(httpRequest: HttpServletRequest): ResponseEntity<Any> {
        if (!isAdmin(httpRequest)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(mapOf("error" to "Solo ADMIN puede probar la conexion OpenAI"))
        }
        val result: CrmOpenAiTestResultDto = llmClient.testConnection()
        auditService.recordAccess(
            operatorUsername = resolveUsername(httpRequest),
            resource = "/crm/settings/openai/test",
            details = "success=${result.success}"
        )
        return ResponseEntity.ok(result)
    }

    private fun isAdmin(request: HttpServletRequest): Boolean =
        CrmAccessPolicy.canManageCrmSecrets(
            request.getAttribute(PlatformAuthFilter.AUTH_USER_TYPE_ATTRIBUTE)?.toString()
        )

    private fun resolveUsername(request: HttpServletRequest): String? =
        request.getAttribute(PlatformAuthFilter.AUTH_USERNAME_ATTRIBUTE)?.toString()
}
