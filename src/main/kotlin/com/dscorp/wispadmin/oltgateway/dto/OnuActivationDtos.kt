package com.dscorp.wispadmin.oltgateway.dto

enum class OltActivationStatus {
    PENDING,
    COMPLETE,
    FAILED,
}

enum class CpeProvisionStatus {
    COMPLETE,
    PENDING,
    NA,
    FAILED,
}

data class OnuActivateRequestDto(
    val oltId: String = "",
    val ponType: String = "gpon",
    val board: String = "",
    val port: String = "",
    val sn: String = "",
    val vlan: String = "",
    val onuType: String = "",
    val zone: String = "",
    val name: String = "",
    val onuMode: String = "",
    val customProfile: String = "",
    val ip: String? = null,
    val ipSegment: String? = null,
    val wifiSsid24: String? = null,
    val wifiPassword24: String? = null,
    val wifiSsid5: String? = null,
    val wifiPassword5: String? = null,
)

data class OnuActivateResponseDto(
    val uniqueExternalId: String? = null,
    val sn: String,
    val oltStatus: OltActivationStatus,
    val cpeStatus: CpeProvisionStatus,
    val message: String? = null,
)

data class OnuActivationStatusDto(
    val uniqueExternalId: String? = null,
    val sn: String,
    val oltStatus: OltActivationStatus,
    val cpeStatus: CpeProvisionStatus,
    val message: String? = null,
    val updatedAtEpochMs: Long = 0,
)

data class CpeCommandResponseDto(
    val accepted: Boolean,
    val status: CpeProvisionStatus,
    val message: String? = null,
)

data class CpeTelemetryDto(
    val sn: String,
    val uniqueExternalId: String? = null,
    val cpeStatus: CpeProvisionStatus,
    val lastInformAt: String? = null,
    val productClass: String? = null,
    val wanIp: String? = null,
    val ssid24: String? = null,
    val ssid5: String? = null,
    val softwareVersion: String? = null,
)
