package com.dscorp.wispadmin.wispadmin.oltclient

data class GatewayOnuActivateRequest(
    val oltId: String,
    val ponType: String,
    val board: String,
    val port: String,
    val sn: String,
    val vlan: String,
    val onuType: String,
    val zone: String,
    val name: String,
    val onuMode: String,
    val customProfile: String,
    val ip: String? = null,
    val ipSegment: String? = null,
    val wifiSsid24: String? = null,
    val wifiPassword24: String? = null,
    val wifiSsid5: String? = null,
    val wifiPassword5: String? = null,
    val pppoeUsername: String? = null,
    val pppoePassword: String? = null,
)

data class GatewayOnuActivateResponse(
    val uniqueExternalId: String? = null,
    val sn: String = "",
    val oltStatus: String = "FAILED",
    val cpeStatus: String = "NA",
    val message: String? = null,
    val deviceId: String? = null,
)

data class GatewayCpeCommandResponse(
    val accepted: Boolean = false,
    val status: String = "FAILED",
    val message: String? = null,
)

data class GatewayCpeTelemetry(
    val sn: String = "",
    val uniqueExternalId: String? = null,
    val cpeStatus: String = "NA",
    val lastInformAt: String? = null,
    val productClass: String? = null,
    val wanIp: String? = null,
    val ssid24: String? = null,
    val ssid5: String? = null,
    val softwareVersion: String? = null,
    val deviceId: String? = null,
)

data class GatewayServicePortsDto(
    val sn: String = "",
    val board: Int = 0,
    val port: Int = 0,
    val ontId: Int = 0,
    val vlans: Set<Int> = emptySet(),
)

data class GatewayRemoveServicePortRequest(
    val vlan: Int,
)

data class GatewayCpeProvisionRequest(
    val sn: String,
    val uniqueExternalId: String? = null,
    val onuType: String? = null,
    val ip: String? = null,
    val ipSegment: String? = null,
    val wanVlanId: Int = 1,
    val wifiSsid24: String? = null,
    val wifiPassword24: String? = null,
    val wifiSsid5: String? = null,
    val wifiPassword5: String? = null,
    val pppoeUsername: String? = null,
    val pppoePassword: String? = null,
)

data class GatewayCpeProvisionResponse(
    val sn: String = "",
    val status: String = "NA",
    val message: String? = null,
    val deviceId: String? = null,
)

data class GatewayCpeWifiRequest(
    val ssid24: String? = null,
    val ssid5: String? = null,
    val passphrase: String? = null,
)

data class GatewayCpeAccessLayout(
    val sn: String = "",
    val productClass: String? = null,
    val connectionRequestUrl: String? = null,
    val lastInformAt: String? = null,
    val wanIpPath: String? = null,
    val wanPppPath: String? = null,
    val hasPppPath: Boolean = false,
    val wanIpSharesPppSlot: Boolean = false,
)
