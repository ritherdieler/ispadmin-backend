package com.dscorp.wispadmin.routeros.port

interface MikrotikClient {
    fun <T> withSession(device: MikrotikDeviceRef, block: (MikrotikSession) -> T): T

    fun closeSession(deviceId: String) {}

    fun isSessionActive(deviceId: String): Boolean = false

    fun activeSessionDeviceIds(): Set<String> = emptySet()
}
