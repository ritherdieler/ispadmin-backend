package com.dscorp.wispadmin.wispadmin.dto

import com.dscorp.wispadmin.wispadmin.data.model.NetworkDevice
import com.dscorp.wispadmin.wispadmin.extensions.NetworkDeviceConnection
import java.io.Serializable

/**
 * A DTO for the {@link com.dscorp.wispadmin.wispadmin.data.model.NetworkDevice} entity
 */
data class NetworkDeviceDto(
    val id: Int? = null,
    val name: String? = null,
    override val password: String? = null,
    override val username: String? = null,
    override val ipAddress: String? = null,
    val networkDeviceType: NetworkDevice.NetworkDeviceType? = null,
    val vlanId: Int? = null,
    val disabled: Boolean = false
) : Serializable, NetworkDeviceConnection