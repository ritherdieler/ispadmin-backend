package com.dscorp.wispadmin.wispadmin.controller

import com.dscorp.wispadmin.wispadmin.security.PlatformAuthFilter
import com.dscorp.wispadmin.wispadmin.service.provisioningv2.OnuRegistrationOperationService
import com.dscorp.wispadmin.wispadmin.service.provisioningv2.OnuRegistrationStartRequest
import com.dscorp.wispadmin.wispadmin.service.provisioningv2.ProvisioningEvent
import com.dscorp.wispadmin.wispadmin.service.provisioningv2.ProvisioningOperation
import com.fasterxml.jackson.databind.JsonNode
import org.springframework.http.MediaType
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.server.ResponseStatusException
import javax.servlet.http.HttpServletRequest

data class RegistrationOperationRevisionRequest(val expectedRevision: Long)
data class RegistrationPhotoResponse(val url: String)

@RestController
@RequestMapping("/onu-registration-operations")
class OnuRegistrationOperationsController(private val service: OnuRegistrationOperationService) {
    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun start(@RequestBody request: OnuRegistrationStartRequest, http: HttpServletRequest): ProvisioningOperation =
        invoke { service.start(operatorId(http), username(http), request) }

    @GetMapping("/active")
    fun active(http: HttpServletRequest): ProvisioningOperation? = invoke { service.active(operatorId(http)) }

    @GetMapping("/admin/unlinked")
    fun unlinked(http: HttpServletRequest): List<ProvisioningOperation> {
        requireAdmin(http)
        return invoke { service.unlinkedPreauthorizations() }
    }

    @GetMapping("/admin/{operationId}")
    fun adminGet(@PathVariable operationId: String, http: HttpServletRequest): ProvisioningOperation {
        requireAdmin(http)
        return invoke { service.adminGet(operationId) }
    }

    @GetMapping("/admin/{operationId}/history")
    fun adminHistory(
        @PathVariable operationId: String,
        @RequestParam(defaultValue = "0") after: Long,
        http: HttpServletRequest,
    ): List<ProvisioningEvent> {
        requireAdmin(http)
        return invoke { service.adminHistory(operationId, after) }
    }

    @GetMapping("/{operationId}")
    fun get(@PathVariable operationId: String, http: HttpServletRequest): ProvisioningOperation =
        invoke { service.get(operatorId(http), operationId) }

    @GetMapping("/{operationId}/history")
    fun history(
        @PathVariable operationId: String,
        @RequestParam(defaultValue = "0") after: Long,
        http: HttpServletRequest,
    ): List<ProvisioningEvent> = invoke { service.history(operatorId(http), operationId, after) }

    @GetMapping("/{operationId}/draft")
    fun draft(@PathVariable operationId: String, http: HttpServletRequest): JsonNode =
        invoke { service.draft(operatorId(http), operationId) ?: throw NoSuchElementException("DRAFT_NOT_FOUND") }

    @PutMapping("/{operationId}/draft")
    fun saveDraft(
        @PathVariable operationId: String,
        @RequestBody request: JsonNode,
        http: HttpServletRequest,
    ): JsonNode = invoke { service.saveDraft(operatorId(http), operationId, request) }

    @PostMapping("/{operationId}/photo", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun savePhoto(
        @PathVariable operationId: String,
        @RequestPart("facadePhoto") file: MultipartFile,
        http: HttpServletRequest,
    ): RegistrationPhotoResponse = RegistrationPhotoResponse(invoke { service.savePhoto(operatorId(http), operationId, file) })

    @PostMapping("/{operationId}/retry-acs")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun retryAcs(
        @PathVariable operationId: String,
        @RequestBody request: RegistrationOperationRevisionRequest,
        http: HttpServletRequest,
    ): ProvisioningOperation = invoke { service.retryAcs(operatorId(http), operationId, request.expectedRevision) }

    @PostMapping("/{operationId}/cancel")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun cancel(
        @PathVariable operationId: String,
        @RequestBody request: RegistrationOperationRevisionRequest,
        http: HttpServletRequest,
    ): ProvisioningOperation = invoke { service.cancel(operatorId(http), operationId, request.expectedRevision) }

    private fun operatorId(http: HttpServletRequest): Long {
        val type = http.getAttribute(PlatformAuthFilter.AUTH_USER_TYPE_ATTRIBUTE)?.toString()?.uppercase()
        if (type !in setOf("ADMIN", "TECHNICIAN", "ACCOUNTANT")) {
            val status = if (type.isNullOrBlank()) HttpStatus.UNAUTHORIZED else HttpStatus.FORBIDDEN
            throw ResponseStatusException(status, "Se requiere un perfil autorizado para registrar suscripciones")
        }
        return (http.getAttribute(PlatformAuthFilter.AUTH_USER_ID_ATTRIBUTE) as? Number)?.toLong()
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sesión de operador requerida")
    }

    private fun username(http: HttpServletRequest): String =
        http.getAttribute(PlatformAuthFilter.AUTH_USERNAME_ATTRIBUTE)?.toString()?.trim()?.takeIf(String::isNotEmpty)
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sesión de operador requerida")

    private fun requireAdmin(http: HttpServletRequest) {
        val type = http.getAttribute(PlatformAuthFilter.AUTH_USER_TYPE_ATTRIBUTE)?.toString()?.uppercase()
        if (type != "ADMIN") throw ResponseStatusException(HttpStatus.FORBIDDEN, "Se requiere perfil administrador")
    }

    private fun <T> invoke(action: () -> T): T = try {
        action()
    } catch (_: NoSuchElementException) {
        throw ResponseStatusException(HttpStatus.NOT_FOUND, "Operación no encontrada")
    } catch (ex: IllegalArgumentException) {
        throw ResponseStatusException(HttpStatus.CONFLICT, "La revisión o los datos de la operación no son válidos", ex)
    } catch (ex: IllegalStateException) {
        throw ResponseStatusException(HttpStatus.CONFLICT, "La acción no está permitida en el estado actual", ex)
    }
}
