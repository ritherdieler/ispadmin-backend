package com.dscorp.wispadmin.wispadmin.adapter

import com.dscorp.wispadmin.netdiag.port.NetDiagDeviceDirectoryPort
import com.dscorp.wispadmin.routeros.config.RouterOsClientProperties
import com.dscorp.wispadmin.routeros.port.MikrotikDeviceRef
import com.dscorp.wispadmin.wispadmin.repository.NetworkDeviceRepository
import com.dscorp.wispadmin.wispadmin.service.mikrotik.MikrotikDeviceRefMapper
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(prefix = "net.diag", name = ["enabled"], havingValue = "true")
class NetDiagDeviceDirectoryAdapter(
    private val networkDeviceRepository: NetworkDeviceRepository,
    private val routerOsClientProperties: RouterOsClientProperties
) : NetDiagDeviceDirectoryPort {

    override fun findMikrotikDeviceRef(targetId: Long): MikrotikDeviceRef? {
        if (targetId > Int.MAX_VALUE || targetId < Int.MIN_VALUE) return null
        val device = networkDeviceRepository.findById(targetId.toInt()).orElse(null) ?: return null
        return MikrotikDeviceRefMapper.toDeviceRef(device, routerOsClientProperties.classic.port)
    }
}
