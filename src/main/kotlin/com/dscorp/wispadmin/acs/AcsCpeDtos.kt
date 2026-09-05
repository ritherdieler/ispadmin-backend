package com.dscorp.wispadmin.acs

enum class CpeStatus {
    NA,
    PENDING,
    COMPLETE,
    FAILED,
}

data class CpeProvisionCommand(
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
)

data class CpeProvisionResult(
    val sn: String,
    val status: CpeStatus,
    val message: String? = null,
    val deviceId: String? = null,
)

data class CpeCommandResult(
    val accepted: Boolean,
    val status: CpeStatus,
    val message: String? = null,
)

data class CpeTelemetryResult(
    val sn: String,
    val uniqueExternalId: String? = null,
    val cpeStatus: CpeStatus,
    val lastInformAt: String? = null,
    val productClass: String? = null,
    val wanIp: String? = null,
    val ssid24: String? = null,
    val ssid5: String? = null,
    val softwareVersion: String? = null,
    val deviceId: String? = null,
    val message: String? = null,
)
