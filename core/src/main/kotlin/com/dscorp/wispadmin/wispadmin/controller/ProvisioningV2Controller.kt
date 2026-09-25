package com.dscorp.wispadmin.wispadmin.controller

import com.dscorp.wispadmin.wispadmin.service.provisioningv2.ProvisioningActionRequest
import com.dscorp.wispadmin.wispadmin.service.provisioningv2.ProvisioningControlService
import com.dscorp.wispadmin.wispadmin.service.provisioningv2.ProvisioningProgress
import com.dscorp.wispadmin.wispadmin.service.provisioningv2.ProvisioningEvent
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException

/** Public controls use the durable Core journal and its server-selected environment. */
@RestController
@RequestMapping("/subscription/{subscriptionId}/provisioning")
class ProvisioningV2Controller(private val service: ProvisioningControlService) {
    @GetMapping
    fun progress(@PathVariable subscriptionId: Int, @RequestParam(required = false) operationId: String?): ProvisioningProgress =
        invoke { if (operationId == null) service.latest(subscriptionId) else service.progress(subscriptionId, operationId) }

    @GetMapping("/history")
    fun history(@PathVariable subscriptionId: Int, @RequestParam operationId: String,
        @RequestParam(defaultValue = "0") after: Long): List<ProvisioningEvent> =
        invoke { service.history(subscriptionId, operationId, after) }

    @PostMapping("/retry")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun retry(@PathVariable subscriptionId: Int, @RequestBody request: ProvisioningActionRequest): ProvisioningProgress =
        invoke { service.retry(subscriptionId, request) }

    @PostMapping("/cancel")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun cancel(@PathVariable subscriptionId: Int, @RequestBody request: ProvisioningActionRequest): ProvisioningProgress =
        invoke { service.cancel(subscriptionId, request) }

    private fun <T> invoke(action: () -> T): T = try {
        action()
    } catch (ex: NoSuchElementException) {
        throw ResponseStatusException(HttpStatus.NOT_FOUND, "Operación no encontrada")
    } catch (ex: IllegalArgumentException) {
        throw ResponseStatusException(HttpStatus.CONFLICT, "La revisión de la operación cambió; actualice el estado")
    } catch (ex: IllegalStateException) {
        throw ResponseStatusException(HttpStatus.CONFLICT, "La acción no está permitida en el estado actual")
    }
}
