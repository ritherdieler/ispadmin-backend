package com.dscorp.wispadmin.oltgateway.adapter

import com.dscorp.wispadmin.netdiag.port.NetDiagOltDescriptor
import com.dscorp.wispadmin.netdiag.port.NetDiagOltDescriptorPort
import com.dscorp.wispadmin.oltgateway.config.OltGatewayProperties
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(prefix = "olt.gateway", name = ["enabled"], havingValue = "true")
class NetDiagOltDescriptorAdapter(
    private val properties: OltGatewayProperties
) : NetDiagOltDescriptorPort {

    override fun descriptor(): NetDiagOltDescriptor = NetDiagOltDescriptor(
        oltId = properties.oltId,
        host = properties.host,
        alarmPollEnabled = properties.sync.alarmEnabled,
        portsPerGponBoard = properties.inventory.defaultPortsPerGponBoard
    )
}
