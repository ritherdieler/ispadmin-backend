package com.dscorp.wispadmin.wispadmin.requestbody

import com.dscorp.wispadmin.wispadmin.data.model.NetworkDevice

data class NetworkDeviceRequest(
    var id: Int,
    var name: String? = null,
    var password: String? = null,
    var username: String? = null,
    var ipAddress: String? = null,
    var networkDeviceType: NetworkDevice.NetworkDeviceType? = null
) {
    fun toModel(): NetworkDevice = NetworkDevice(
        id = id,
        name = name,
        password = password,
        username = username,
        ipAddress = ipAddress,
        networkDeviceType = networkDeviceType?: NetworkDevice.NetworkDeviceType.FIBER_ROUTER
    )
}