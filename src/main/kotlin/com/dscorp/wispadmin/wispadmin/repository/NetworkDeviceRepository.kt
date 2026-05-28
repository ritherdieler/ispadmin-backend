package com.dscorp.wispadmin.wispadmin.repository;

import com.dscorp.wispadmin.wispadmin.data.model.NetworkDevice
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface NetworkDeviceRepository : JpaRepository<NetworkDevice, Int> {

    fun findByNetworkDeviceType(networkDeviceType: NetworkDevice.NetworkDeviceType): List<NetworkDevice>
    
    fun findByName(name: String): NetworkDevice?

    //find all except CLOUD_CORE_ROUTER type
    @Query("SELECT n FROM NetworkDevice n WHERE n.networkDeviceType = 'GENERIC'")
    fun findGenericNetworkDevices(): List<NetworkDevice>

    @Query("SELECT n FROM NetworkDevice n WHERE n.networkDeviceType = 'WIRELESS_ROUTER' OR n.networkDeviceType = 'FIBER_ROUTER'")
    fun findWirelessAndFiberDevices(): List<NetworkDevice>
}