package com.dscorp.wispadmin.netdiag.controller

import com.dscorp.wispadmin.netdiag.dto.AlertIngestRequestDto
import com.dscorp.wispadmin.netdiag.dto.AlertIngestResponseDto
import com.dscorp.wispadmin.netdiag.dto.IncidentDetailDto
import com.dscorp.wispadmin.netdiag.dto.IncidentSummaryDto
import com.dscorp.wispadmin.netdiag.dto.NetDiagHealthResponseDto
import com.dscorp.wispadmin.netdiag.dto.SyslogIngestRequestDto
import com.dscorp.wispadmin.netdiag.dto.SyslogIngestResponseDto
import com.dscorp.wispadmin.netdiag.dto.TrapIngestRequestDto
import com.dscorp.wispadmin.netdiag.dto.TrapIngestResponseDto
import com.dscorp.wispadmin.netdiag.dto.MaintenanceWindowDto
import com.dscorp.wispadmin.netdiag.dto.MaintenanceWindowRequestDto
import com.dscorp.wispadmin.netdiag.dto.SilenceIncidentRequestDto
import com.dscorp.wispadmin.netdiag.exception.NetDiagConflictException
import com.dscorp.wispadmin.netdiag.service.NetDiagMaintenanceService
import com.dscorp.wispadmin.netdiag.service.AlertEvaluator
import com.dscorp.wispadmin.netdiag.service.AlertSignalExtractor
import com.dscorp.wispadmin.netdiag.service.NetDiagIncidentQueryService
import com.dscorp.wispadmin.netdiag.service.NetDiagLlmContextService
import com.dscorp.wispadmin.netdiag.service.NetDiagSnmpTrapIngestService
import com.dscorp.wispadmin.netdiag.service.SyslogIngestAdapter
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

import java.time.Instant
import java.time.format.DateTimeParseException

@RestController
@RequestMapping("/api/netdiag")
@ConditionalOnProperty(prefix = "net.diag", name = ["enabled"], havingValue = "true")
@Tag(name = "NetDiag", description = "Diagnóstico de red / NOC")
class NetDiagController(
    private val incidentQueryService: NetDiagIncidentQueryService,
    private val llmContextService: NetDiagLlmContextService,
    private val maintenanceService: NetDiagMaintenanceService,
    private val alertEvaluator: AlertEvaluator,
    private val signalExtractor: AlertSignalExtractor,
    private val trapIngestService: NetDiagSnmpTrapIngestService,
    private val syslogIngestAdapter: SyslogIngestAdapter
) {

    @GetMapping("/health")
    @Operation(summary = "Health del módulo netdiag", description = "No requiere API key.")
    fun health(): NetDiagHealthResponseDto {
        return NetDiagHealthResponseDto(status = "UP", module = "netdiag")
    }

    @GetMapping("/incidents")
    @Operation(summary = "Lista de incidentes NOC")
    @SecurityRequirement(name = "NetDiagApiKey")
    fun listIncidents(
        @RequestParam(required = false) severity: String?,
        @RequestParam(required = false) status: String?,
        @RequestParam(required = false) targetId: Long?,
        @RequestParam(required = false) dateFrom: String?,
        @RequestParam(required = false) dateTo: String?
    ): List<IncidentSummaryDto> {
        return incidentQueryService.listIncidents(
            severity = severity,
            status = status,
            targetId = targetId,
            dateFrom = dateFrom,
            dateTo = dateTo
        )
    }

    @GetMapping("/incidents/{id}")
    @Operation(summary = "Detalle de incidente NOC con timeline")
    @SecurityRequirement(name = "NetDiagApiKey")
    fun getIncident(@PathVariable id: Long): IncidentDetailDto {
        return incidentQueryService.getIncident(id)
    }

    @GetMapping(
        value = ["/incidents/{id}/llm-context"],
        produces = [MediaType.TEXT_PLAIN_VALUE, "text/markdown"]
    )
    @Operation(summary = "Bundle markdown para LLM (text/plain)")
    @SecurityRequirement(name = "NetDiagApiKey")
    fun llmContext(@PathVariable id: Long): String {
        return llmContextService.buildMarkdown(id)
    }

    @GetMapping("/incidents/{id}/diagnostic-json")
    @Operation(summary = "Bundle JSON estructurado para diagnóstico")
    @SecurityRequirement(name = "NetDiagApiKey")
    fun diagnosticJson(@PathVariable id: Long): Map<String, Any?> {
        return llmContextService.buildDiagnosticJson(id)
    }

    @PostMapping("/incidents/{id}/ack")
    @Operation(summary = "Confirmar (ack) incidente")
    @SecurityRequirement(name = "NetDiagApiKey")
    fun acknowledge(@PathVariable id: Long): IncidentDetailDto {
        return incidentQueryService.acknowledge(id)
    }

    @PostMapping("/incidents/{id}/resolve")
    @Operation(summary = "Resolver incidente")
    @SecurityRequirement(name = "NetDiagApiKey")
    fun resolve(@PathVariable id: Long): IncidentDetailDto {
        return incidentQueryService.resolve(id)
    }

    @PostMapping("/incidents/{id}/silence")
    @Operation(summary = "Silenciar notificaciones del incidente")
    @SecurityRequirement(name = "NetDiagApiKey")
    fun silence(
        @PathVariable id: Long,
        @RequestBody(required = false) request: SilenceIncidentRequestDto?
    ): IncidentDetailDto {
        val until = parseSilenceUntil(request)
        return incidentQueryService.silence(id, until)
    }

    @GetMapping("/maintenance-windows")
    @Operation(summary = "Lista ventanas de mantenimiento")
    @SecurityRequirement(name = "NetDiagApiKey")
    fun listMaintenanceWindows(): List<MaintenanceWindowDto> {
        return maintenanceService.list()
    }

    @PostMapping("/maintenance-windows")
    @Operation(summary = "Crear ventana de mantenimiento")
    @SecurityRequirement(name = "NetDiagApiKey")
    fun createMaintenanceWindow(@RequestBody request: MaintenanceWindowRequestDto): MaintenanceWindowDto {
        val startsAt = parseInstantRequired(request.startsAt, "startsAt")
        val endsAt = parseInstantRequired(request.endsAt, "endsAt")
        if (request.title.isBlank()) {
            throw NetDiagConflictException("title is required")
        }
        return maintenanceService.create(
            targetId = request.targetId,
            title = request.title,
            description = request.description,
            startsAt = startsAt,
            endsAt = endsAt
        )
    }

    @DeleteMapping("/maintenance-windows/{id}")
    @Operation(summary = "Eliminar ventana de mantenimiento")
    @SecurityRequirement(name = "NetDiagApiKey")
    fun deleteMaintenanceWindow(@PathVariable id: Long) {
        maintenanceService.delete(id)
    }

    @PostMapping("/alerts/ingest")
    @Operation(summary = "Ingest de alerta externa (p.ej. OLT PON_DOWN)")
    @SecurityRequirement(name = "NetDiagApiKey")
    fun ingestAlert(@RequestBody request: AlertIngestRequestDto): AlertIngestResponseDto {
        val signal = signalExtractor.fromIngest(
            targetId = request.targetId,
            reasonCode = request.reasonCode,
            severity = request.severity,
            title = request.title,
            component = request.component,
            details = request.details
        )
        val result = alertEvaluator.evaluateIngest(request.targetId, listOf(signal))
        return AlertIngestResponseDto(
            decisions = result.decisions,
            openedIncidentIds = result.openedIncidentIds,
            suppressed = result.suppressed
        )
    }

    @PostMapping("/traps/ingest")
    @Operation(summary = "Ingest SNMP trap (HTTP bridge o relay)")
    @SecurityRequirement(name = "NetDiagApiKey")
    fun ingestTrap(@RequestBody request: TrapIngestRequestDto): TrapIngestResponseDto {
        return trapIngestService.ingest(request)
    }

    @PostMapping("/syslog/ingest")
    @Operation(summary = "Ingest syslog MikroTik (loop-protect / link / temp / PPP)")
    @SecurityRequirement(name = "NetDiagApiKey")
    fun ingestSyslog(@RequestBody request: SyslogIngestRequestDto): SyslogIngestResponseDto {
        val result = syslogIngestAdapter.ingest(request.targetId, request.message)
        return SyslogIngestResponseDto(
            decisions = result.decisions,
            openedIncidentIds = result.openedIncidentIds,
            suppressed = result.suppressed
        )
    }

    private fun parseSilenceUntil(request: SilenceIncidentRequestDto?): Instant? {
        if (request == null) return null
        if (!request.until.isNullOrBlank()) {
            return parseInstantRequired(request.until!!, "until")
        }
        val minutes = request.durationMinutes
        if (minutes != null && minutes > 0) {
            return Instant.now().plusSeconds(minutes * 60)
        }
        return null
    }

    private fun parseInstantRequired(raw: String, field: String): Instant {
        if (raw.isBlank()) {
            throw NetDiagConflictException("$field is required")
        }
        return try {
            Instant.parse(raw)
        } catch (_: DateTimeParseException) {
            throw NetDiagConflictException("Invalid $field: $raw")
        }
    }
}
