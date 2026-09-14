package com.dscorp.wispadmin.wispadmin.controller

import com.dscorp.wispadmin.wispadmin.dto.FilterRuleDto
import com.dscorp.wispadmin.wispadmin.dto.FilterRuleOperationRequest
import com.dscorp.wispadmin.wispadmin.dto.FilterRuleOperationResponse
import com.dscorp.wispadmin.wispadmin.repository.NetworkDeviceRepository
import com.dscorp.wispadmin.wispadmin.service.MikroTikConnectionService
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/filter-rules")
@CrossOrigin(origins = ["*"])
class FilterRuleController(
    private val mikrotikConnectionService: MikroTikConnectionService,
    private val networkDeviceRepository: NetworkDeviceRepository
) {

    companion object {
        private val logger = LoggerFactory.getLogger(FilterRuleController::class.java)
    }

    @GetMapping("/debt-cut/{deviceId}")
    fun getDebtorsAddressList(@PathVariable deviceId: Int): ResponseEntity<List<FilterRuleDto>> {
        logger.info("Obteniendo address-list 'deudores' para dispositivo {}", deviceId)
        val device = networkDeviceRepository.findById(deviceId).orElse(null)
            ?: return ResponseEntity.notFound().build()
        val entries = mikrotikConnectionService.getDebtorsAddressList(device)
        val dtos = entries.map { data ->
            FilterRuleDto(
                id = data[".id"] ?: "",
                chain = null,
                action = null,
                srcAddress = data["address"],
                dstAddress = null,
                protocol = null,
                srcPort = null,
                dstPort = null,
                comment = data["comment"],
                disabled = data["disabled"]?.equals("true", ignoreCase = true) ?: false,
                bytes = null,
                packets = null,
                inInterface = null,
                outInterface = null
            )
        }
        logger.info("Obtenidas {} entradas en 'deudores'", dtos.size)
        return ResponseEntity.ok(dtos)
    }

    @PostMapping("/enable")
    fun enableDebtors(@RequestBody request: FilterRuleOperationRequest): ResponseEntity<FilterRuleOperationResponse> {
        logger.info("Habilitando {} entradas en 'deudores' en dispositivo {}", request.ruleIds.size, request.deviceId)
        val device = networkDeviceRepository.findById(request.deviceId).orElse(null)
            ?: return ResponseEntity.notFound().build()
        val results = mikrotikConnectionService.enableMultipleAddressListEntries(device, request.ruleIds)
        val successCount = results.values.count { it }
        val failedCount = results.size - successCount
        val response = FilterRuleOperationResponse(
            success = successCount > 0,
            message = if (successCount == results.size) "Todas las entradas fueron habilitadas exitosamente" else "Se habilitaron $successCount de ${results.size}. $failedCount fallaron.",
            results = results,
            totalRules = results.size,
            successfulRules = successCount,
            failedRules = failedCount
        )
        return ResponseEntity.ok(response)
    }

    @PostMapping("/disable")
    fun disableDebtors(@RequestBody request: FilterRuleOperationRequest): ResponseEntity<FilterRuleOperationResponse> {
        logger.info("Deshabilitando {} entradas en 'deudores' en dispositivo {}", request.ruleIds.size, request.deviceId)
        val device = networkDeviceRepository.findById(request.deviceId).orElse(null)
            ?: return ResponseEntity.notFound().build()
        val results = mikrotikConnectionService.disableMultipleAddressListEntries(device, request.ruleIds)
        val successCount = results.values.count { it }
        val failedCount = results.size - successCount
        val response = FilterRuleOperationResponse(
            success = successCount > 0,
            message = if (successCount == results.size) "Todas las entradas fueron deshabilitadas exitosamente" else "Se deshabilitaron $successCount de ${results.size}. $failedCount fallaron.",
            results = results,
            totalRules = results.size,
            successfulRules = successCount,
            failedRules = failedCount
        )
        return ResponseEntity.ok(response)
    }

    @PostMapping("/enable/{deviceId}/{entryId}")
    fun enableSingleDebtor(
        @PathVariable deviceId: Int,
        @PathVariable("entryId") ruleId: String
    ): ResponseEntity<FilterRuleOperationResponse> {
        logger.info("Habilitando address-list entry {} en dispositivo {}", ruleId, deviceId)
        val device = networkDeviceRepository.findById(deviceId).orElse(null)
            ?: return ResponseEntity.notFound().build()
        val success = mikrotikConnectionService.enableAddressListEntry(device, ruleId)
        val response = FilterRuleOperationResponse(
            success = success,
            message = if (success) "Entrada habilitada exitosamente" else "Error al habilitar la entrada",
            results = mapOf(ruleId to success),
            totalRules = 1,
            successfulRules = if (success) 1 else 0,
            failedRules = if (success) 0 else 1
        )
        return ResponseEntity.ok(response)
    }

    @PostMapping("/disable/{deviceId}/{entryId}")
    fun disableSingleDebtor(
        @PathVariable deviceId: Int,
        @PathVariable("entryId") ruleId: String
    ): ResponseEntity<FilterRuleOperationResponse> {
        logger.info("Deshabilitando address-list entry {} en dispositivo {}", ruleId, deviceId)
        val device = networkDeviceRepository.findById(deviceId).orElse(null)
            ?: return ResponseEntity.notFound().build()
        val success = mikrotikConnectionService.disableAddressListEntry(device, ruleId)
        val response = FilterRuleOperationResponse(
            success = success,
            message = if (success) "Entrada deshabilitada exitosamente" else "Error al deshabilitar la entrada",
            results = mapOf(ruleId to success),
            totalRules = 1,
            successfulRules = if (success) 1 else 0,
            failedRules = if (success) 0 else 1
        )
        return ResponseEntity.ok(response)
    }
}
