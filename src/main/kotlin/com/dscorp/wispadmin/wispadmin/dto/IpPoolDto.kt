package com.dscorp.wispadmin.wispadmin.dto

class IpPoolDto(
    var id: Int,
    var ipSegment: String = "",
    var createdAt: Long,
    val hostDevice: NetworkDeviceDto,
    val isEligible: Boolean
)