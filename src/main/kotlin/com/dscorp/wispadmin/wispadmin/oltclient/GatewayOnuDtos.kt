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
)

data class GatewayOnuActivateResponse(
    val uniqueExternalId: String? = null,
    val sn: String = "",
    val oltStatus: String = "FAILED",
    val cpeStatus: String = "NA",
    val message: String? = null,
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
)
