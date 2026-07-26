package com.dscorp.wispadmin.wispadmin.service.mikrotik

import com.dscorp.wispadmin.routeros.port.MikrotikDeviceRef
import com.dscorp.wispadmin.wispadmin.data.model.NetworkDevice
import com.dscorp.wispadmin.wispadmin.extensions.NetworkDeviceConnection
import com.dscorp.wispadmin.wispadmin.extensions.NetworkDeviceConnectionManager

object MikrotikDeviceRefMapper {

    fun toDeviceRef(device: NetworkDeviceConnection, classicPort: Int): MikrotikDeviceRef {
        val connectionData = NetworkDeviceConnectionManager.getConnectionData(device)
        val deviceId = when (device) {
            is NetworkDevice -> device.id.toString()
            else -> connectionData.ipAddress ?: "unknown"
        }
        return MikrotikDeviceRef(
            id = deviceId,
            host = requireNotNull(connectionData.ipAddress) { "MikroTik host is required" },
            port = classicPort,
            username = requireNotNull(connectionData.username) { "MikroTik username is required" },
            password = requireNotNull(connectionData.password) { "MikroTik password is required" }
        )
    }
}
