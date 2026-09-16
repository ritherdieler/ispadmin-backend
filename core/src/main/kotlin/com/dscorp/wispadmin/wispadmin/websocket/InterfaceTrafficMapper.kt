package com.dscorp.wispadmin.wispadmin.websocket

object InterfaceTrafficMapper {
    fun toWsRow(interfaceInfo: Map<String, String>): Map<String, String> {
        return mapOf(
            "name" to (interfaceInfo["name"] ?: ""),
            "type" to (interfaceInfo["type"] ?: ""),
            "rxBytes" to (interfaceInfo["rx-byte"] ?: "0"),
            "txBytes" to (interfaceInfo["tx-byte"] ?: "0"),
            "rxPackets" to (interfaceInfo["rx-packet"] ?: "0"),
            "txPackets" to (interfaceInfo["tx-packet"] ?: "0"),
            "running" to (interfaceInfo["running"] ?: ""),
            "disabled" to (interfaceInfo["disabled"] ?: ""),
        )
    }
}
