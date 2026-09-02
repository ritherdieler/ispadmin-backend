package com.dscorp.wispadmin.wispadmin.data.model

data class Onu(
    val sn: String = "",
    var board: String = "",
    var onu: String = "",
    var onu_type_id: String = "",
    var onu_type_name: String = "",
    var pon_type: String = "",
    var port: String = "",
    var olt_id: String = "",
)
