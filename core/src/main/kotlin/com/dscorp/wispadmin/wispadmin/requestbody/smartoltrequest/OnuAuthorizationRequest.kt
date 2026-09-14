package com.dscorp.wispadmin.wispadmin.requestbody.smartoltrequest

data class OnuAuthorizationRequest(
    val olt_id: String,
    val pon_type: String,
    val board: String,
    val port: String,
    val sn: String,
    val vlan: String,
    val onu_type: String,
    val zone: String,
    val name: String,
    val onu_mode: String,
    val custom_profile: String,
)