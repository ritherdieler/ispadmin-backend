package com.dscorp.wispadmin.wispadmin.repository;

import com.dscorp.wispadmin.wispadmin.data.model.NetworkDevice
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface NetworkDeviceRepository : JpaRepository<NetworkDevice, Int> {

    fun findByNetworkDeviceType(networkDeviceType: NetworkDevice.NetworkDeviceType): List<NetworkDevice>
    
    fun findByName(name: String): NetworkDevice?

    @Query("SELECT n FROM NetworkDevice n WHERE n.networkDeviceType = 'GENERIC'")
    fun findGenericNetworkDevices(): List<NetworkDevice>

    @Query("SELECT n FROM NetworkDevice n WHERE n.networkDeviceType = 'WIRELESS_ROUTER' OR n.networkDeviceType = 'FIBER_ROUTER'")
    fun findWirelessAndFiberDevices(): List<NetworkDevice>

    @Query("SELECT n FROM NetworkDevice n WHERE n.networkDeviceType = 'CLOUD_CORE_ROUTER' AND n.disabled = false")
    fun findActiveCloudCoreRouters(): List<NetworkDevice>
}