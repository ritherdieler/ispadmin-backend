package com.dscorp.wispadmin.wispadmin.dto

import com.dscorp.wispadmin.wispadmin.data.model.Onu

data class OnuDto(
    val board: String = "",
    val olt_id: String = "",
    val onu: String = "",
    val onu_type_id: String = "",
    val onu_type_name: String = "",
    val pon_type: String = "",
    val port: String = "",
    val sn: String = "",
) {
    fun toModel(): Onu = Onu(
        board = board,
        olt_id = olt_id,
        onu = onu,
        onu_type_id = onu_type_id,
        onu_type_name = onu_type_name,
        pon_type = pon_type,
        port = port,
        sn = sn
    )


}


data class OnuDtoAdministrative(
    val sn: String = "",
    val name: String = "",
    val mgmt_ip_service_port: String? = "",
    val authorization_date: String = "",
    val custom_template_name: String = "",
    val onu_type_name: String = "",
    val onu: String = "",
    val port: String = "",
    val board: String = "",
    val olt_id: String = "",
    val olt_name: String = "",
    val unique_external_id: String = "",
)
