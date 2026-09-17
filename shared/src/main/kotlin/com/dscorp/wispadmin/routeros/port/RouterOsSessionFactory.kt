package com.dscorp.wispadmin.routeros.port

fun interface RouterOsSessionFactory {
    fun open(hostDeviceId: Int): MikrotikSession
}
