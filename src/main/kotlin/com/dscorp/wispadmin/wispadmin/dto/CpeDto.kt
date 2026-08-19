package com.dscorp.wispadmin.wispadmin.dto

import com.dscorp.wispadmin.wispadmin.cpe.AcsState
import com.dscorp.wispadmin.wispadmin.cpe.GponState
import com.dscorp.wispadmin.wispadmin.cpe.WanManagement

/** Optical link seen by the OLT, independent from the CWMP agent. */
data class CpeGponStatusDto(
    val state: GponState,
    val rxDbm: String?,
    val txDbm: String?,
)

/** CWMP agent synchronization, independent from the optical link. */
data class CpeAcsStatusDto(
    val state: AcsState,
    val lastInform: String?,
    val reachable: Boolean,
)

data class CpeCapabilitiesDto(
    val canWriteWanViaTr069: Boolean,
    val canWriteWanViaOmci: Boolean,
    val canWriteWifiViaTr069: Boolean,
    val wanManagedBy: WanManagement,
    val vendor: String?,
    val model: String?,
)

data class CpeStatusDto(
    val online: Boolean,
    val rxDbm: String?,
    val lastInform: String?,
    val sn: String?,
    val gponStatus: CpeGponStatusDto,
    val acsStatus: CpeAcsStatusDto,
    val capabilities: CpeCapabilitiesDto,
)

data class UpdateWifiRequest(
    var ssid: String = "",
    var password: String = "",
)

data class UpdateWifiResponseDto(
    val message: String,
    val subscriptionId: Int,
)

data class CpeNetworkConfigRequest(
    var ipAddress: String = "",
    var subnetMask: String = "",
    var gateway: String = "",
    var dnsPrimary: String = "",
    var dnsSecondary: String? = null,
    var vlanId: Int? = null,
)

data class CpeWifiConfigRequest(
    var ssid: String = "",
    var password: String = "",
)

data class UpdateCpeConfigRequest(
    var network: CpeNetworkConfigRequest? = null,
    var wifi: CpeWifiConfigRequest? = null,
)

data class UpdateCpeConfigResponseDto(
    val message: String,
    val subscriptionId: Int,
    val appliedNetwork: Boolean,
    val appliedWifi: Boolean,
    /** Channel that actually wrote the WAN settings, null when nothing was applied. */
    val networkChannel: WanManagement? = null,
    val warnings: List<String> = emptyList(),
)
