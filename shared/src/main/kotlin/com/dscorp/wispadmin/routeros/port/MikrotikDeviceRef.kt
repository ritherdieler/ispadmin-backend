package com.dscorp.wispadmin.routeros.port

data class MikrotikDeviceRef(
    val id: String,
    val host: String,
    val port: Int,
    val username: String,
    val password: String
)
