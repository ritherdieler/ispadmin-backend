package com.dscorp.wispadmin.wispadmin.response

import com.dscorp.wispadmin.wispadmin.dto.OnuDtoAdministrative

data class OnuBySnResponse(
    val onus: List<Onu>,
    val response_code: String,
    val status: Boolean
) {
    constructor() : this(emptyList(), "", false)


    fun toAdministrativeDto() = OnuDtoAdministrative(
        sn = onus[0].sn,
        name = onus[0].name,
        mgmt_ip_service_port = onus[0].mgmt_ip_service_port,
        authorization_date = onus[0].authorization_date,
        custom_template_name = onus[0].custom_template_name,
        onu_type_name = onus[0].onu_type_name,
        onu = onus[0].onu,
        port = onus[0].port,
        board = onus[0].board,
        olt_id = onus[0].olt_id,
        olt_name = onus[0].olt_name,
        unique_external_id = onus[0].unique_external_id
    )
}