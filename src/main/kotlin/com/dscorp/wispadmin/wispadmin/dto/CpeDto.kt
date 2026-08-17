package com.dscorp.wispadmin.wispadmin.dto

data class CpeStatusDto(
    val online: Boolean,
    val rxDbm: String?,
    val lastInform: String?,
    val sn: String?,
)

data class UpdateWifiRequest(
    var ssid: String = "",
    var password: String = "",
)

data class UpdateWifiResponseDto(
    val message: String,
    val subscriptionId: Int,
)
