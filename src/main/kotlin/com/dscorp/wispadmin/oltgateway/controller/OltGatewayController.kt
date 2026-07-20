package com.dscorp.wispadmin.oltgateway.controller

import com.dscorp.wispadmin.oltgateway.api.SmartOltOnuBySnResponseDto
import com.dscorp.wispadmin.oltgateway.api.SmartOltUnconfiguredOnusResponseDto
import com.dscorp.wispadmin.oltgateway.dto.ConfiguredOnuPageDto
import com.dscorp.wispadmin.oltgateway.dto.ErrorResponseDto
import com.dscorp.wispadmin.oltgateway.dto.HealthResponseDto
import com.dscorp.wispadmin.oltgateway.dto.OltInfoDto
import com.dscorp.wispadmin.oltgateway.dto.OnuDetailDto
import com.dscorp.wispadmin.oltgateway.dto.OnuSummaryListDto
import com.dscorp.wispadmin.oltgateway.dto.OpticalInfoDto
import com.dscorp.wispadmin.oltgateway.dto.SignalPollResultDto
import com.dscorp.wispadmin.oltgateway.dto.SyncResultDto
import com.dscorp.wispadmin.oltgateway.dto.SyncStatusDto
import com.dscorp.wispadmin.oltgateway.service.OltGatewayQueryFacade
import com.dscorp.wispadmin.oltgateway.service.OltInventorySyncService
import com.dscorp.wispadmin.oltgateway.service.OltSignalPollService
import com.dscorp.wispadmin.wispadmin.config.OpenApiConfig
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import javax.validation.constraints.Max
import javax.validation.constraints.Min
import javax.validation.constraints.Pattern

@RestController
@RequestMapping("/api/olt-gateway")
@Validated
@ConditionalOnProperty(prefix = "olt.gateway", name = ["enabled"], havingValue = "true")
@Tag(name = "OLT Gateway", description = "Lectura Huawei MA5608T vía SSH CLI (reemplazo gradual SmartOLT)")
class OltGatewayController(
    private val queryFacade: OltGatewayQueryFacade,
    private val inventorySyncService: OltInventorySyncService,
    private val signalPollService: OltSignalPollService
) {

    @GetMapping("/health")
    @Operation(summary = "Health del gateway", description = "Comprueba estado del módulo y latencia hacia la OLT. No requiere API key.")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Estado del gateway",
                content = [Content(schema = Schema(implementation = HealthResponseDto::class))]
            )
        ]
    )
    fun health(): HealthResponseDto = queryFacade.health()

    @GetMapping("/olt/info")
    @Operation(summary = "Info de la OLT", description = "Versión y boards de la OLT.")
    @SecurityRequirement(name = OpenApiConfig.OLT_GATEWAY_SECURITY_SCHEME)
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Información de la OLT",
                content = [Content(schema = Schema(implementation = OltInfoDto::class))]
            ),
            ApiResponse(
                responseCode = "401",
                description = "API key ausente o inválida",
                content = [Content(schema = Schema(implementation = ErrorResponseDto::class))]
            ),
            ApiResponse(
                responseCode = "502",
                description = "OLT inalcanzable",
                content = [Content(schema = Schema(implementation = ErrorResponseDto::class))]
            )
        ]
    )
    fun oltInfo(): OltInfoDto = queryFacade.oltInfo()

    @GetMapping("/onus/autofind")
    @Operation(summary = "ONUs en autofind", description = "Lista ONUs no confirmadas. Contrato compatible SmartOLT.")
    @SecurityRequirement(name = OpenApiConfig.OLT_GATEWAY_SECURITY_SCHEME)
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Lista autofind",
                content = [Content(schema = Schema(implementation = SmartOltUnconfiguredOnusResponseDto::class))]
            ),
            ApiResponse(
                responseCode = "401",
                description = "API key ausente o inválida",
                content = [Content(schema = Schema(implementation = ErrorResponseDto::class))]
            )
        ]
    )
    fun autofind(): SmartOltUnconfiguredOnusResponseDto = queryFacade.autofind()

    @GetMapping("/onus/by-sn/{sn}")
    @Operation(summary = "ONU por serial", description = "Busca ONU por SN. Contrato compatible SmartOLT.")
    @SecurityRequirement(name = OpenApiConfig.OLT_GATEWAY_SECURITY_SCHEME)
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "ONU encontrada",
                content = [Content(schema = Schema(implementation = SmartOltOnuBySnResponseDto::class))]
            ),
            ApiResponse(
                responseCode = "401",
                description = "API key ausente o inválida",
                content = [Content(schema = Schema(implementation = ErrorResponseDto::class))]
            ),
            ApiResponse(
                responseCode = "404",
                description = "ONU no encontrada",
                content = [Content(schema = Schema(implementation = ErrorResponseDto::class))]
            )
        ]
    )
    fun bySn(
        @PathVariable
        @Pattern(regexp = "^[A-Za-z0-9]{8,20}$")
        sn: String
    ): SmartOltOnuBySnResponseDto = queryFacade.bySn(sn)

    @GetMapping("/onus")
    @Operation(summary = "Listado de ONUs", description = "Resumen nativo de ONUs en slots 0/1.")
    @SecurityRequirement(name = OpenApiConfig.OLT_GATEWAY_SECURITY_SCHEME)
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Resumen de ONUs",
                content = [Content(schema = Schema(implementation = OnuSummaryListDto::class))]
            ),
            ApiResponse(
                responseCode = "401",
                description = "API key ausente o inválida",
                content = [Content(schema = Schema(implementation = ErrorResponseDto::class))]
            )
        ]
    )
    fun listOnus(): OnuSummaryListDto = queryFacade.listOnus()

    @GetMapping("/onus/configured")
    @Operation(summary = "ONUs configuradas (DB)", description = "Listado paginado desde olt_mgr_onu + status. Sin SSH.")
    @SecurityRequirement(name = OpenApiConfig.OLT_GATEWAY_SECURITY_SCHEME)
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Página de ONUs configuradas",
                content = [Content(schema = Schema(implementation = ConfiguredOnuPageDto::class))]
            ),
            ApiResponse(
                responseCode = "401",
                description = "API key ausente o inválida",
                content = [Content(schema = Schema(implementation = ErrorResponseDto::class))]
            )
        ]
    )
    fun listConfiguredOnus(
        @RequestParam(defaultValue = "0") @Min(0) page: Int,
        @RequestParam(defaultValue = "50") @Min(1) @Max(200) size: Int
    ): ConfiguredOnuPageDto = inventorySyncService.listConfigured(page, size)

    @PostMapping("/admin/sync/inventory")
    @Operation(summary = "Sync inventario manual", description = "Ejecuta sync SSH→DB inventory+status y devuelve SyncResult.")
    @SecurityRequirement(name = OpenApiConfig.OLT_GATEWAY_SECURITY_SCHEME)
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Resultado del sync",
                content = [Content(schema = Schema(implementation = SyncResultDto::class))]
            ),
            ApiResponse(
                responseCode = "401",
                description = "API key ausente o inválida",
                content = [Content(schema = Schema(implementation = ErrorResponseDto::class))]
            )
        ]
    )
    fun syncInventory(): SyncResultDto {
        val result = inventorySyncService.syncInventory()
        return SyncResultDto(
            inserted = result.inserted,
            updated = result.updated,
            softDeleted = result.softDeleted,
            unchanged = result.unchanged,
            durationMs = result.durationMs,
            skippedReason = result.skippedReason,
            error = result.error
        )
    }

    @PostMapping("/admin/sync/signal")
    @Operation(summary = "Sync señal óptica manual", description = "Poll óptico SSH→DB (signal_poll) y devuelve SignalPollResult.")
    @SecurityRequirement(name = OpenApiConfig.OLT_GATEWAY_SECURITY_SCHEME)
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Resultado del signal poll",
                content = [Content(schema = Schema(implementation = SignalPollResultDto::class))]
            ),
            ApiResponse(
                responseCode = "401",
                description = "API key ausente o inválida",
                content = [Content(schema = Schema(implementation = ErrorResponseDto::class))]
            )
        ]
    )
    fun syncSignal(): SignalPollResultDto {
        val result = signalPollService.pollSignals()
        return SignalPollResultDto(
            slotsPolled = result.slotsPolled,
            portsPolled = result.portsPolled,
            onusUpdated = result.onusUpdated,
            durationMs = result.durationMs,
            skippedReason = result.skippedReason,
            error = result.error
        )
    }

    @GetMapping("/admin/sync/status")
    @Operation(summary = "Estado del sync", description = "Inventory + signal + profundidad del bus CLI.")
    @SecurityRequirement(name = OpenApiConfig.OLT_GATEWAY_SECURITY_SCHEME)
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Estado del sync",
                content = [Content(schema = Schema(implementation = SyncStatusDto::class))]
            ),
            ApiResponse(
                responseCode = "401",
                description = "API key ausente o inválida",
                content = [Content(schema = Schema(implementation = ErrorResponseDto::class))]
            )
        ]
    )
    fun syncStatus(): SyncStatusDto {
        val inventory = inventorySyncService.status()
        val signal = signalPollService.status()
        return SyncStatusDto(
            running = inventory.running,
            lastStartedAt = inventory.lastStartedAt,
            lastResult = inventory.lastResult,
            signalRunning = signal.running,
            signalLastStartedAt = signal.lastStartedAt,
            signalLastResult = signal.lastResult,
            busQueueDepth = maxOf(inventory.busQueueDepth, signal.busQueueDepth),
            busBusyJobType = inventory.busBusyJobType ?: signal.busBusyJobType
        )
    }

    @GetMapping("/onus/{slot}/{port}/{ontId}")
    @Operation(summary = "Detalle de ONU", description = "Detalle nativo por slot/port/ontId.")
    @SecurityRequirement(name = OpenApiConfig.OLT_GATEWAY_SECURITY_SCHEME)
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Detalle de la ONU",
                content = [Content(schema = Schema(implementation = OnuDetailDto::class))]
            ),
            ApiResponse(
                responseCode = "401",
                description = "API key ausente o inválida",
                content = [Content(schema = Schema(implementation = ErrorResponseDto::class))]
            ),
            ApiResponse(
                responseCode = "404",
                description = "ONU no encontrada",
                content = [Content(schema = Schema(implementation = ErrorResponseDto::class))]
            )
        ]
    )
    fun onuDetail(
        @PathVariable @Min(0) @Max(1) slot: Int,
        @PathVariable @Min(0) @Max(15) port: Int,
        @PathVariable @Min(0) @Max(127) ontId: Int
    ): OnuDetailDto = queryFacade.onuDetail(slot, port, ontId)

    @GetMapping("/onus/{slot}/{port}/{ontId}/optical")
    @Operation(summary = "Info óptica de ONU", description = "RX/TX, temperatura, voltaje y bias.")
    @SecurityRequirement(name = OpenApiConfig.OLT_GATEWAY_SECURITY_SCHEME)
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Datos ópticos",
                content = [Content(schema = Schema(implementation = OpticalInfoDto::class))]
            ),
            ApiResponse(
                responseCode = "401",
                description = "API key ausente o inválida",
                content = [Content(schema = Schema(implementation = ErrorResponseDto::class))]
            ),
            ApiResponse(
                responseCode = "404",
                description = "ONU no encontrada",
                content = [Content(schema = Schema(implementation = ErrorResponseDto::class))]
            )
        ]
    )
    fun optical(
        @PathVariable @Min(0) @Max(1) slot: Int,
        @PathVariable @Min(0) @Max(15) port: Int,
        @PathVariable @Min(0) @Max(127) ontId: Int
    ): OpticalInfoDto = queryFacade.optical(slot, port, ontId)
}
