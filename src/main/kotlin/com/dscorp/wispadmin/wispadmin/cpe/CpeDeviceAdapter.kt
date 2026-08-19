package com.dscorp.wispadmin.wispadmin.cpe

import com.dscorp.wispadmin.wispadmin.dto.CpeNetworkConfigRequest

/** Optical link state reported by the OLT. */
enum class GponState { ONLINE, OFFLINE, UNKNOWN }

/** CWMP synchronization state reported by the ACS. */
enum class AcsState { SYNCED, STALE, UNKNOWN }

data class CpeDeviceDescriptor(
    val serialNumber: String,
    val deviceId: String,
    val vendor: String?,
    val model: String?,
    val lastInform: String?,
    val reachable: Boolean,
) {
    fun toProfile(): CpeDeviceProfile = CpeDeviceProfile(
        serialNumber = serialNumber,
        vendor = vendor,
        model = model,
    )
}

data class CpeConfigurationOutcome(
    val appliedNetwork: Boolean,
    val appliedWifi: Boolean,
    val warnings: List<String> = emptyList(),
)

/** Port towards the CWMP layer, so orchestration never talks to GenieACS directly. */
interface CpeDeviceAdapter {

    fun describe(serialNumber: String): CpeDeviceDescriptor?

    fun applyConfiguration(
        serialNumber: String,
        network: CpeNetworkConfigRequest?,
        ssid: String?,
        passphrase: String?,
    ): CpeConfigurationOutcome
}
