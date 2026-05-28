package com.dscorp.wispadmin.wispadmin.controller

import com.dscorp.wispadmin.wispadmin.dto.FilterRuleDto
import com.dscorp.wispadmin.wispadmin.dto.FilterRuleOperationRequest
import com.dscorp.wispadmin.wispadmin.dto.FilterRuleOperationResponse
import com.dscorp.wispadmin.wispadmin.repository.NetworkDeviceRepository
import com.dscorp.wispadmin.wispadmin.service.MikroTikConnectionService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/filter-rules")
@CrossOrigin(origins = ["*"])
class FilterRuleController(
    @Autowired private val mikrotikConnectionService: MikroTikConnectionService,
    @Autowired private val networkDeviceRepository: NetworkDeviceRepository
) {
    
    private val logger = LoggerFactory.getLogger(FilterRuleController::class.java)
    
    /**
     * Obtiene todos los filter rules de corte por deuda de un dispositivo
     */
    @GetMapping("/debt-cut/{deviceId}")
    fun getDebtorsAddressList(@PathVariable deviceId: Int): ResponseEntity<List<FilterRuleDto>> {
        logger.info("🔍 Obteniendo address-list 'deudores' para dispositivo $deviceId")
        return try {
            val device = networkDeviceRepository.findById(deviceId).orElse(null)
                ?: return ResponseEntity.notFound().build()
            val entries = mikrotikConnectionService.getDebtorsAddressList(device)
            val dtos = entries.map { data ->
                // Mapear address-list entry -> FilterRuleDto para mantener compatibilidad con FE
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
            logger.info("✅ Obtenidas ${dtos.size} entradas en 'deudores'")
            ResponseEntity.ok(dtos)
        } catch (e: Exception) {
            logger.error("❌ Error obteniendo address-list deudores: ${e.message}")
            ResponseEntity.internalServerError().build()
        }
    }
    
    /**
     * Activa filter rules específicos
     */
    @PostMapping("/enable")
    fun enableDebtors(@RequestBody request: FilterRuleOperationRequest): ResponseEntity<FilterRuleOperationResponse> {
        logger.info("✅ Habilitando ${request.ruleIds.size} entradas en 'deudores' en dispositivo ${request.deviceId}")
        return try {
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
            ResponseEntity.ok(response)
        } catch (e: Exception) {
            logger.error("❌ Error habilitando address-list: ${e.message}")
            ResponseEntity.internalServerError().body(
                FilterRuleOperationResponse(success = false, message = "Error interno del servidor: ${e.message}")
            )
        }
    }
    
    /**
     * Desactiva filter rules específicos
     */
    @PostMapping("/disable")
    fun disableDebtors(@RequestBody request: FilterRuleOperationRequest): ResponseEntity<FilterRuleOperationResponse> {
        logger.info("🚫 Deshabilitando ${request.ruleIds.size} entradas en 'deudores' en dispositivo ${request.deviceId}")
        return try {
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
            ResponseEntity.ok(response)
        } catch (e: Exception) {
            logger.error("❌ Error deshabilitando address-list: ${e.message}")
            ResponseEntity.internalServerError().body(
                FilterRuleOperationResponse(success = false, message = "Error interno del servidor: ${e.message}")
            )
        }
    }
    
    /**
     * Activa un filter rule individual
     */
    @PostMapping("/enable/{deviceId}/{entryId}")
    fun enableSingleDebtor(
        @PathVariable deviceId: Int,
        @PathVariable("entryId") ruleId: String
    ): ResponseEntity<FilterRuleOperationResponse> {
        logger.info("✅ Habilitando address-list entry $ruleId en dispositivo $deviceId")
        return try {
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
            ResponseEntity.ok(response)
        } catch (e: Exception) {
            logger.error("❌ Error habilitando address-list entry: ${e.message}")
            ResponseEntity.internalServerError().body(
                FilterRuleOperationResponse(success = false, message = "Error interno del servidor: ${e.message}")
            )
        }
    }
    
    /**
     * Desactiva un filter rule individual  
     */
    @PostMapping("/disable/{deviceId}/{entryId}")
    fun disableSingleDebtor(
        @PathVariable deviceId: Int,
        @PathVariable("entryId") ruleId: String
    ): ResponseEntity<FilterRuleOperationResponse> {
        logger.info("🚫 Deshabilitando address-list entry $ruleId en dispositivo $deviceId")
        return try {
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
            ResponseEntity.ok(response)
        } catch (e: Exception) {
            logger.error("❌ Error deshabilitando address-list entry: ${e.message}")
            ResponseEntity.internalServerError().body(
                FilterRuleOperationResponse(success = false, message = "Error interno del servidor: ${e.message}")
            )
        }
    }
} 