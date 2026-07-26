package com.dscorp.wispadmin.netdiag.port

import com.dscorp.wispadmin.routeros.port.MikrotikDeviceRef

interface NetDiagDeviceDirectoryPort {
    fun findMikrotikDeviceRef(targetId: Long): MikrotikDeviceRef?
}
