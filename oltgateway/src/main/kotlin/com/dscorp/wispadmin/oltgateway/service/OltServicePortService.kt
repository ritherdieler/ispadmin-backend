package com.dscorp.wispadmin.oltgateway.service

import com.dscorp.wispadmin.oltgateway.port.OltInventoryPort
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException

data class OnuServicePortsDto(
    val sn: String,
    val board: Int,
    val port: Int,
    val ontId: Int,
    val vlans: Set<Int>,
)

@Service
@ConditionalOnProperty(prefix = "olt.gateway", name = ["enabled"], havingValue = "true")
class OltServicePortService(
    private val inventory: OltInventoryPort,
    private val commandService: OltGatewayCommandService,
) {
    fun listBySn(sn: String): OnuServicePortsDto {
        val onu = inventory.findBySn(sn) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "ONU not found for SN=$sn")
        val output = commandService.displayServicePorts(onu.board, onu.port, onu.onuIndex)
        return OnuServicePortsDto(
            sn = onu.sn,
            board = onu.board,
            port = onu.port,
            ontId = onu.onuIndex,
            vlans = commandService.parseServicePortVlans(output),
        )
    }

    fun removeVlan(sn: String, vlan: Int): OnuServicePortsDto {
        val onu = inventory.findBySn(sn) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "ONU not found for SN=$sn")
        commandService.removeServicePort(onu.board, onu.port, onu.onuIndex, vlan)
        val output = commandService.displayServicePorts(onu.board, onu.port, onu.onuIndex)
        return OnuServicePortsDto(
            sn = onu.sn,
            board = onu.board,
            port = onu.port,
            ontId = onu.onuIndex,
            vlans = commandService.parseServicePortVlans(output),
        )
    }
}
