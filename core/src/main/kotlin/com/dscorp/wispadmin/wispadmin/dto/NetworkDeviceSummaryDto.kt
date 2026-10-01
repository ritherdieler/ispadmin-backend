package com.dscorp.wispadmin.wispadmin.dto

import com.dscorp.wispadmin.wispadmin.data.model.NetworkDevice

data class NetworkDeviceSummaryDto(
    val id: Int? = null,
    val name: String? = null,
    val ipAddress: String? = null,
    val networkDeviceType: NetworkDevice.NetworkDeviceType? = null,
    val vlanId: Int? = null,
    val disabled: Boolean = false,
)

fun NetworkDevice.toSummaryDto() = NetworkDeviceSummaryDto(
    id = id,
    name = name,
    ipAddress = ipAddress,
    networkDeviceType = networkDeviceType,
    vlanId = vlanId,
    disabled = disabled,
)
