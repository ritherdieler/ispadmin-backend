package com.dscorp.wispadmin.netdiag.controller

import com.dscorp.wispadmin.netdiag.dto.IncidentDetailDto
import com.dscorp.wispadmin.netdiag.dto.IncidentSummaryDto
import com.dscorp.wispadmin.netdiag.dto.NetDiagHealthResponseDto
import com.dscorp.wispadmin.netdiag.service.NetDiagIncidentQueryService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/netdiag")
@ConditionalOnProperty(prefix = "net.diag", name = ["enabled"], havingValue = "true")
@Tag(name = "NetDiag", description = "Diagnóstico de red / NOC (scaffold)")
class NetDiagController(
    private val incidentQueryService: NetDiagIncidentQueryService
) {

    @GetMapping("/health")
    @Operation(summary = "Health del módulo netdiag", description = "No requiere API key.")
    fun health(): NetDiagHealthResponseDto {
        return NetDiagHealthResponseDto(status = "UP", module = "netdiag")
    }

    @GetMapping("/incidents")
    @Operation(summary = "Lista de incidentes NOC")
    @SecurityRequirement(name = "NetDiagApiKey")
    fun listIncidents(): List<IncidentSummaryDto> {
        return incidentQueryService.listIncidents()
    }

    @GetMapping("/incidents/{id}")
    @Operation(summary = "Detalle de incidente NOC")
    @SecurityRequirement(name = "NetDiagApiKey")
    fun getIncident(@PathVariable id: Long): IncidentDetailDto {
        return incidentQueryService.getIncident(id)
    }
}
