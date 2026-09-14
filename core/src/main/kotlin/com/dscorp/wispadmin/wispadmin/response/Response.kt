package com.dscorp.wispadmin.wispadmin.response

data class Response(
    val board: String,
    val olt_id: String,
    val onu: String,
    val onu_type_id: String,
    val onu_type_name: String,
    val pon_type: String,
    val port: String,
    val sn: String
){
    constructor() : this("", "", "", "", "", "", "", "")
}