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
    val pppoeUsername: String? = null,
    val pppoePassword: String? = null,
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

data class CpeWifiCommand(
    val ssid24: String? = null,
    val ssid5: String? = null,
    val passphrase: String? = null,
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
    val wifiAssociated2g: Int? = null,
    val wifiAssociated5g: Int? = null,
    val wifiAssociatedTotal: Int? = null,
    val wifiObservedAt: String? = null,
    val wifiQualityStatus: String? = null,
)

data class CpeInformNotifyRequest(
    val deviceId: String? = null,
    val serial: String? = null,
    /** Leaves the Inform provision declared in this CWMP session; absent means read the NBI. */
    val payload: String? = null,
)

data class CpeAccessLayout(
    val sn: String,
    val productClass: String? = null,
    val connectionRequestUrl: String? = null,
    val lastInformAt: String? = null,
    val wanIpPath: String? = null,
    val wanPppPath: String? = null,
    val hasPppPath: Boolean = false,
    val wanIpSharesPppSlot: Boolean = false,
)
