package com.dscorp.wispadmin.oltgateway.controller

import com.dscorp.wispadmin.oltgateway.api.AuthorizeOnuFormDto
import com.dscorp.wispadmin.oltgateway.api.MoveOnuFormDto
import com.dscorp.wispadmin.oltgateway.api.SmartOltActionResponseDto
import com.dscorp.wispadmin.oltgateway.api.SmartOltOnuBySnResponseDto
import com.dscorp.wispadmin.oltgateway.api.SmartOltUnconfiguredOnusResponseDto
import com.dscorp.wispadmin.oltgateway.api.UpdateWanFormDto
import com.dscorp.wispadmin.oltgateway.service.OltManagerFacade
import com.dscorp.wispadmin.wispadmin.config.OpenApiConfig
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping(path = ["/api/olt-gateway", "/api"])
@ConditionalOnProperty(prefix = "olt.gateway", name = ["enabled"], havingValue = "true")
@Tag(name = "OLT Gateway SmartOLT Compat", description = "Aliases HTTP compatibles con SmartOLT (8 ops)")
@SecurityRequirement(name = OpenApiConfig.OLT_GATEWAY_SECURITY_SCHEME)
class SmartOltCompatController(
    private val oltManagerFacade: OltManagerFacade
) {

    @GetMapping("/onu/unconfigured_onus")
    @Operation(summary = "Unconfigured ONUs (SmartOLT alias)")
    fun unconfiguredOnus(): SmartOltUnconfiguredOnusResponseDto = oltManagerFacade.unconfiguredOnus()

    @GetMapping("/onu/get_onus_details_by_sn/{sn}")
    @Operation(summary = "ONU details by SN (SmartOLT alias)")
    fun getOnusDetailsBySn(@PathVariable sn: String): SmartOltOnuBySnResponseDto =
        oltManagerFacade.getOnusDetailsBySn(sn)

    @PostMapping(
        path = ["/onu/authorize_onu"],
        consumes = [MediaType.APPLICATION_FORM_URLENCODED_VALUE, MediaType.MULTIPART_FORM_DATA_VALUE]
    )
    @Operation(summary = "Authorize ONU (SmartOLT alias)")
    fun authorizeOnu(
        @RequestParam(required = false, defaultValue = "") olt_id: String,
        @RequestParam(required = false, defaultValue = "gpon") pon_type: String,
        @RequestParam(required = false, defaultValue = "") board: String,
        @RequestParam(required = false, defaultValue = "") port: String,
        @RequestParam(required = false, defaultValue = "") sn: String,
        @RequestParam(required = false, defaultValue = "") vlan: String,
        @RequestParam(required = false, defaultValue = "") onu_type: String,
        @RequestParam(required = false, defaultValue = "") zone: String,
        @RequestParam(required = false, defaultValue = "") name: String,
        @RequestParam(required = false, defaultValue = "") onu_mode: String,
        @RequestParam(required = false, defaultValue = "") custom_profile: String
    ): SmartOltActionResponseDto {
        return oltManagerFacade.authorizeOnu(
            AuthorizeOnuFormDto(
                olt_id = olt_id,
                pon_type = pon_type,
                board = board,
                port = port,
                sn = sn,
                vlan = vlan,
                onu_type = onu_type,
                zone = zone,
                name = name,
                onu_mode = onu_mode,
                custom_profile = custom_profile
            )
        )
    }

    @PostMapping(
        path = ["/onu/move/{sn}"],
        consumes = [MediaType.APPLICATION_FORM_URLENCODED_VALUE, MediaType.MULTIPART_FORM_DATA_VALUE]
    )
    @Operation(summary = "Move ONU (SmartOLT alias)")
    fun moveOnu(
        @PathVariable sn: String,
        @RequestParam(required = false, defaultValue = "") olt_id: String,
        @RequestParam(required = false, defaultValue = "") board: String,
        @RequestParam(required = false, defaultValue = "") port: String
    ): SmartOltActionResponseDto {
        return oltManagerFacade.moveOnu(sn, MoveOnuFormDto(olt_id = olt_id, board = board, port = port))
    }

    @PostMapping("/onu/delete/{externalId}")
    @Operation(summary = "Delete ONU (SmartOLT alias)")
    fun deleteOnu(@PathVariable externalId: String): SmartOltActionResponseDto =
        oltManagerFacade.deleteOnu(externalId)

    @PostMapping("/onu/reboot/{externalId}")
    @Operation(summary = "Reboot ONU (SmartOLT alias)")
    fun rebootOnu(@PathVariable externalId: String): SmartOltActionResponseDto =
        oltManagerFacade.rebootOnu(externalId)

    @PostMapping(
        path = ["/onu/set_wan_mode/{externalId}"],
        consumes = [MediaType.APPLICATION_FORM_URLENCODED_VALUE, MediaType.MULTIPART_FORM_DATA_VALUE]
    )
    @Operation(summary = "Set ONU WAN mode (SmartOLT alias)")
    fun setWanMode(
        @PathVariable externalId: String,
        @RequestParam(required = false, defaultValue = "") wan_mode: String,
        @RequestParam(required = false, defaultValue = "") vlan: String,
        @RequestParam(required = false, defaultValue = "") ip_address: String,
        @RequestParam(required = false, defaultValue = "") subnet_mask: String,
        @RequestParam(required = false, defaultValue = "") default_gateway: String,
        @RequestParam(required = false, defaultValue = "") dns1: String,
        @RequestParam(required = false, defaultValue = "") dns2: String
    ): SmartOltActionResponseDto {
        return oltManagerFacade.updateOnuWan(
            externalId,
            UpdateWanFormDto(
                wan_mode = wan_mode,
                vlan = vlan,
                ip_address = ip_address,
                subnet_mask = subnet_mask,
                default_gateway = default_gateway,
                dns1 = dns1,
                dns2 = dns2
            )
        )
    }

    @PostMapping(
        path = ["/onu/update_vlan/{externalId}"],
        consumes = [MediaType.APPLICATION_FORM_URLENCODED_VALUE, MediaType.MULTIPART_FORM_DATA_VALUE]
    )
    @Operation(summary = "Update ONU VLAN (SmartOLT alias)")
    fun updateVlan(
        @PathVariable externalId: String,
        @RequestParam(required = false, defaultValue = "") vlan: String
    ): SmartOltActionResponseDto = oltManagerFacade.updateOnuVlan(externalId, vlan)
}
