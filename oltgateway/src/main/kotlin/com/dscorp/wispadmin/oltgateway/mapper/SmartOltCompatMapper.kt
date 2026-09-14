package com.dscorp.wispadmin.oltgateway.mapper

import com.dscorp.wispadmin.oltgateway.api.SmartOltOnuBySnResponseDto
import com.dscorp.wispadmin.oltgateway.api.SmartOltOnuDto
import com.dscorp.wispadmin.oltgateway.api.SmartOltUnconfiguredItemDto
import com.dscorp.wispadmin.oltgateway.api.SmartOltUnconfiguredOnusResponseDto
import com.dscorp.wispadmin.oltgateway.domain.entity.OltMgrOnu
import com.dscorp.wispadmin.oltgateway.domain.entity.OltMgrOnuStatusCurrent
import com.dscorp.wispadmin.oltgateway.parser.ParsedAutofindOnt
import com.dscorp.wispadmin.oltgateway.parser.ParsedOnuBySn
import org.springframework.stereotype.Component

@Component
class SmartOltCompatMapper {

    fun toUnconfirmedOnuResponse(
        items: List<ParsedAutofindOnt>,
        oltId: String
    ): SmartOltUnconfiguredOnusResponseDto {
        val responses = items.map { item ->
            SmartOltUnconfiguredItemDto(
                board = item.slot.toString(),
                olt_id = oltId,
                onu = "",
                onu_type_id = "",
                onu_type_name = item.equipmentId.orEmpty(),
                pon_type = "gpon",
                port = item.port.toString(),
                sn = item.sn
            )
        }
        return SmartOltUnconfiguredOnusResponseDto(response = responses, status = true)
    }

    fun toOnuBySnResponse(
        parsed: ParsedOnuBySn?,
        oltId: String
    ): SmartOltOnuBySnResponseDto {
        if (parsed == null) {
            return SmartOltOnuBySnResponseDto(onus = emptyList(), response_code = "404", status = false)
        }
        val uniqueExternalId = "${oltId}_${parsed.slot}_${parsed.port}_${parsed.ontId}"
        val onu = SmartOltOnuDto(
            administrative_status = parsed.runState.orEmpty(),
            board = parsed.slot.toString(),
            custom_template_name = parsed.lineProfileName.orEmpty(),
            name = parsed.description.orEmpty(),
            olt_id = oltId,
            olt_name = oltId,
            onu = parsed.ontId.toString(),
            pon_type = "gpon",
            port = parsed.port.toString(),
            sn = parsed.sn,
            unique_external_id = uniqueExternalId
        )
        return SmartOltOnuBySnResponseDto(onus = listOf(onu), response_code = "200", status = true)
    }

    fun toOnuBySnResponseFromDb(
        onu: OltMgrOnu,
        status: OltMgrOnuStatusCurrent?,
        oltLogicalId: String
    ): SmartOltOnuBySnResponseDto {
        val dto = SmartOltOnuDto(
            address = onu.address.orEmpty(),
            administrative_status = status?.runState ?: onu.administrativeStatus,
            authorization_date = onu.authorizationDate?.toString().orEmpty(),
            board = onu.board.toString(),
            custom_template_name = onu.customProfile ?: onu.lineProfileName.orEmpty(),
            default_gateway = onu.defaultGateway.orEmpty(),
            dns1 = onu.dns1.orEmpty(),
            dns2 = onu.dns2.orEmpty(),
            ip_address = onu.ipAddress.orEmpty(),
            mgmt_ip_address = onu.mgmtIpAddress.orEmpty(),
            mgmt_ip_mode = onu.mgmtIpMode.orEmpty(),
            mgmt_ip_vlan = onu.mgmtVlanId?.toString().orEmpty(),
            mode = onu.mode.orEmpty(),
            name = onu.name.orEmpty(),
            olt_id = oltLogicalId,
            olt_name = oltLogicalId,
            onu = onu.onuIndex.toString(),
            onu_type_id = onu.onuType?.id?.toString().orEmpty(),
            onu_type_name = onu.onuTypeName.orEmpty(),
            pon_type = onu.ponType,
            port = onu.port.toString(),
            sn = onu.sn,
            subnet_mask = onu.subnetMask.orEmpty(),
            unique_external_id = onu.externalId,
            vlan = onu.mainVlanId?.toString().orEmpty(),
            wan_mode = onu.wanMode.orEmpty(),
            zone_id = onu.zone?.id?.toString().orEmpty(),
            zone_name = onu.zoneName.orEmpty()
        )
        return SmartOltOnuBySnResponseDto(onus = listOf(dto), response_code = "200", status = true)
    }
}
