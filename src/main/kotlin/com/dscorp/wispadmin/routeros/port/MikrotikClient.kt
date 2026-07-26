package com.dscorp.wispadmin.routeros.port

interface MikrotikClient {
    fun <T> withSession(device: MikrotikDeviceRef, block: (MikrotikSession) -> T): T
}
