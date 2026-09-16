package com.dscorp.wispadmin.wispadmin.service.genieacs

import com.dscorp.wispadmin.wispadmin.data.model.Tr069ProvisionStatus
import java.time.LocalDateTime

data class Tr069ProvisionRequest(
    val onuSerial: String?,
    val onuTypeName: String?,
    val ip: String?,
    val ipSegment: String?,
    val wifiSsid24: String?,
    val wifiPassword24: String?,
    val wifiSsid5: String?,
    val wifiPassword5: String?,
    val waitTimeoutMs: Long? = null,
    val wanVlanId: Int,
    val connectionName: String? = null,
    val identityOnly: Boolean = false,
    val pppoeUsername: String? = null,
    val pppoePassword: String? = null,
    val onPhase: ((String) -> Unit)? = null,
) {
    fun usesPppoe(): Boolean =
        !pppoeUsername.isNullOrBlank() && !pppoePassword.isNullOrBlank()
}

data class Tr069AcsSnapshot(
    val serialSuffix: String? = null,
    val lastInformAt: LocalDateTime? = null,
    val productClass: String? = null,
    val oui: String? = null,
    val manufacturer: String? = null,
    val connectionRequestUrl: String? = null,
    val softwareVersion: String? = null,
    val hardwareVersion: String? = null,
    val lastBootAt: LocalDateTime? = null,
    val wanIpCache: String? = null,
    val ssid24: String? = null,
    val ssid5: String? = null,
    val lastTaskId: String? = null,
    val lastTaskStatus: String? = null,
    val lastTaskAt: LocalDateTime? = null,
)

data class Tr069ProvisionOutcome(
    val status: Tr069ProvisionStatus,
    val deviceId: String? = null,
    val error: String? = null,
    val message: String? = null,
    val acsSnapshot: Tr069AcsSnapshot? = null,
)

data class Tr069ParameterValue(
    val path: String,
    val value: String,
    val type: String,
)
