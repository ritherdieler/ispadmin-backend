package com.dscorp.wispadmin.wispadmin.mapper

import com.dscorp.wispadmin.wispadmin.data.model.IpPool
import com.dscorp.wispadmin.wispadmin.data.model.NetworkDevice
import com.dscorp.wispadmin.wispadmin.data.model.Place
import com.dscorp.wispadmin.wispadmin.data.model.User
import com.dscorp.wispadmin.wispadmin.dto.IpPoolDto
import com.dscorp.wispadmin.wispadmin.dto.NetworkDeviceDto
import com.dscorp.wispadmin.wispadmin.dto.PlaceDto
import com.dscorp.wispadmin.wispadmin.dto.UserDto


fun User.toDto() = UserDto(
    id = id,
    name = name,
    lastName = lastName,
    type = type,
    username = username,
    verified = verified,
    phone = phone,
    dni = dni,
    email = email
)

fun NetworkDevice.toDto() = NetworkDeviceDto(
    id = id,
    name = name,
    password = password,
    username = username,
    ipAddress = ipAddress,
    networkDeviceType = networkDeviceType,
    vlanId = vlanId,
    disabled = disabled
)

fun List<NetworkDevice>.toDto() = map {
    NetworkDeviceDto(
        id = it.id,
        name = it.name,
        password = it.password,
        username = it.username,
        ipAddress = it.ipAddress,
        networkDeviceType = it.networkDeviceType,
        vlanId = it.vlanId,
        disabled = it.disabled
    )
}

fun IpPool.toDto() = IpPoolDto(
    id = id,
    ipSegment = ipSegment,
    createdAt = createdAt,
    hostDevice = hostDevice?.toDto() ?: NetworkDeviceDto(),
    isEligible = isEligible
)

fun Place.toDto() = PlaceDto(
    id = id,
    abbreviation = null,
    name = name,
    latitude = latitude,
    longitude = longitude
)

