package com.dscorp.wispadmin.servicehealth.controller

import com.dscorp.wispadmin.servicehealth.port.CpeInspectionPort
import com.dscorp.wispadmin.servicehealth.port.SubscriptionDirectoryPort
import com.dscorp.wispadmin.servicehealth.service.IdentityService
import com.dscorp.wispadmin.servicehealth.repository.RemoteActionRepository
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import javax.servlet.http.HttpServletRequest

@RestController
@RequestMapping("/subscription/{id}/tr069")
class CpeInspectionController(
    private val access: HealthAccess,
    private val directory: SubscriptionDirectoryPort,
    private val identity: IdentityService,
    private val port: CpeInspectionPort,
    private val actions: RemoteActionRepository,
) {
    @GetMapping("/summary")
    fun summary(@PathVariable id: Int, request: HttpServletRequest): JsonNode {
        access.require(request)
        return port.summary(serial(id))
    }

    @GetMapping("/tree")
    fun tree(@PathVariable id: Int, @RequestParam(required = false) parent: String?,
             @RequestParam(required = false, name = "q") query: String?, request: HttpServletRequest): JsonNode {
        access.require(request, true)
        return port.tree(serial(id), parent, query)
    }

    @GetMapping("/faults/current")
    fun currentFaults(@PathVariable id: Int, request: HttpServletRequest): JsonNode {
        access.require(request, true)
        return port.currentFaults(serial(id))
    }

    @GetMapping("/faults/history")
    fun faultHistory(@PathVariable id: Int, @RequestParam(defaultValue = "0") page: Int,
                     @RequestParam(defaultValue = "20") size: Int, request: HttpServletRequest): JsonNode {
        access.require(request, true)
        require(page >= 0 && size in 1..100) { "Invalid pagination" }
        val sn = serial(id)
        val result = port.faultHistory(sn, page, size)
        val linked = result.deepCopy<JsonNode>() as? ObjectNode ?: return result
        for (item in linked.path("items")) {
            val taskId = item.path("taskId").asText(null)?.takeIf { it.matches(Regex("[A-Za-z0-9_.:-]{1,128}")) }
                ?: continue
            val action = actions.findTopBySubscriptionIdAndTaskIdOrderByCreatedAtDesc(id, taskId)
            if (action?.acsDeviceId?.equals(sn, ignoreCase = true) == true && item is ObjectNode) {
                action.id?.let { item.put("actionId", it) }
            }
        }
        return linked
    }

    private fun serial(id: Int): String {
        val ref = directory.find(id) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Subscription not found")
        val sn = ref.onuSn?.trim()?.takeIf { it.isNotEmpty() }
            ?: throw ResponseStatusException(HttpStatus.CONFLICT, "Subscription has no ONU")
        val linkedDeviceId = ref.tr069DeviceId?.trim()?.takeIf { it.isNotEmpty() }
        if (identity.resolveOnu(sn) != id ||
            (linkedDeviceId != null && identity.resolveAcs(linkedDeviceId) != id)) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "ONU or ACS identity is ambiguous")
        }
        return sn
    }
}
