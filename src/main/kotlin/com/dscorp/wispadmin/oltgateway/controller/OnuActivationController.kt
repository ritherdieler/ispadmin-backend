package com.dscorp.wispadmin.oltgateway.controller

import com.dscorp.wispadmin.oltgateway.config.OltGatewayOpenApi
import com.dscorp.wispadmin.oltgateway.dto.CpeCommandResponseDto
import com.dscorp.wispadmin.oltgateway.dto.CpeTelemetryDto
import com.dscorp.wispadmin.oltgateway.dto.OnuActivateRequestDto
import com.dscorp.wispadmin.oltgateway.dto.OnuActivateResponseDto
import com.dscorp.wispadmin.oltgateway.dto.OnuActivationStatusDto
import com.dscorp.wispadmin.oltgateway.service.OnuActivationService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

@RestController
@RequestMapping("/api/olt-gateway")
@ConditionalOnProperty(prefix = "olt.gateway", name = ["enabled"], havingValue = "true")
@Tag(name = "OLT Gateway ONU activation", description = "OLT authorize + CPE via ACS WAR")
@SecurityRequirement(name = OltGatewayOpenApi.SECURITY_SCHEME)
class OnuActivationController(
    private val activationService: OnuActivationService,
) {
    @PostMapping("/onu/activate")
    @Operation(summary = "Authorize ONU on OLT and kick ACS; returns partial if CPE still pending")
    fun activate(@RequestBody request: OnuActivateRequestDto): OnuActivateResponseDto =
        activationService.activate(request)

    @GetMapping("/onus/by-sn/{sn}/activation")
    fun activationBySn(@PathVariable sn: String): OnuActivationStatusDto =
        activationService.statusBySn(sn)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "No activation for SN=$sn")

    @GetMapping("/onus/by-external-id/{externalId}/activation")
    fun activationByExternalId(@PathVariable externalId: String): OnuActivationStatusDto =
        activationService.statusByExternalId(externalId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "No activation for id=$externalId")

    @PostMapping("/onus/{sn}/cpe/reboot")
    fun reboot(@PathVariable sn: String): CpeCommandResponseDto = activationService.reboot(sn)

    @PostMapping("/onus/{sn}/cpe/wifi-refresh")
    fun wifiRefresh(@PathVariable sn: String): CpeCommandResponseDto = activationService.wifiRefresh(sn)

    @GetMapping("/onus/{sn}/cpe/telemetry")
    fun telemetry(@PathVariable sn: String): CpeTelemetryDto =
        activationService.telemetry(sn)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "No CPE telemetry for SN=$sn")
}
