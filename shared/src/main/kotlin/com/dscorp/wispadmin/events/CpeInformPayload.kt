package com.dscorp.wispadmin.events

import java.time.Instant

data class CpeInformStation(
    val macNormalized: String,
    val band: String,
    val observedAt: Instant,
    val displayName: String? = null,
    val rssi: Double? = null,
    val snr: Double? = null,
    val noise: Double? = null,
    val rxRate: Double? = null,
    val txRate: Double? = null,
    val packetsTx: Long? = null,
    val packetsRx: Long? = null,
)

data class CpeInformPayload(
    val sn: String,
    val deviceId: String,
    val informAt: Instant,
    val model: String? = null,
    val observedAt: Instant? = null,
    val associated2g: Int? = null,
    val associated5g: Int? = null,
    val associatedDeviceCount: Int? = null,
    val lanDeviceCount: Int? = null,
    val qualityStatus: String = "MISSING",
    val complete: Boolean = false,
    val errorReason: String? = null,
    val stations: List<CpeInformStation> = emptyList(),
)
