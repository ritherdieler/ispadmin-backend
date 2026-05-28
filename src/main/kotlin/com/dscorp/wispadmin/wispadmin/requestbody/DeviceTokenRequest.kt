package com.dscorp.wispadmin.wispadmin.requestbody

data class DeviceTokenRequest(
    val userId: Int,
    val deviceToken: String
)
