package com.dscorp.wispadmin.events

import java.time.Instant

data class OnuOpticalBatchItem(
    val sn: String,
    val onuExternalId: String,
    val onuRxDbm: Double,
    val onuTxDbm: Double,
    val oltRxDbm: Double,
    val polledAt: Instant,
    val temperatureC: Double? = null,
    val distanceM: Int? = null,
    val biasCurrentMa: Double? = null,
    val runState: String? = null,
)

data class OnuOpticalBatchPayload(
    val oltId: Long,
    val slot: Int,
    val port: Int,
    val polledAt: Instant,
    val onus: List<OnuOpticalBatchItem>,
)
