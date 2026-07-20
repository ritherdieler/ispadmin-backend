package com.dscorp.wispadmin.wispadmin.data.model

import com.dscorp.wispadmin.wispadmin.dto.NetworkDeviceDto
import com.dscorp.wispadmin.wispadmin.extensions.NetworkDeviceConnection
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.EnumType
import javax.persistence.Enumerated
import javax.persistence.GeneratedValue
import javax.persistence.Id
import javax.persistence.OneToMany

@Entity
data class NetworkDevice(
    @Id
    @GeneratedValue
    var id: Int,
    var name: String? = null,
    override var password: String? = null,
    override var username: String? = null,
    override var ipAddress: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "varchar(255) default 'FIBER_ROUTER'")
    var networkDeviceType: NetworkDeviceType = NetworkDeviceType.FIBER_ROUTER,

    @Column(name = "vlan_id")
    var vlanId: Int? = null,

    @Column(nullable = false)
    var disabled: Boolean = false
    ) : NetworkDeviceConnection {
    enum class NetworkDeviceType {
        FIBER_ROUTER, CLOUD_CORE_ROUTER, WIRELESS_ROUTER, GENERIC
    }
    
    fun toDto(): NetworkDeviceDto {
        return NetworkDeviceDto(
            id = id,
            name = name,
            password = password,
            username = username,
            ipAddress = ipAddress,
            networkDeviceType = networkDeviceType,
            vlanId = vlanId,
            disabled = disabled
        )
    }
}

