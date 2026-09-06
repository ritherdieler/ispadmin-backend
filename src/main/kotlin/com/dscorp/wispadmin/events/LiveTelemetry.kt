package com.dscorp.wispadmin.events

import java.time.Duration
import java.time.Instant

interface HealthSnapshotCache {
    fun getSummaryJson(subscriptionId: Int): String?
    fun putSummaryJson(subscriptionId: Int, json: String, ttl: Duration = Duration.ofSeconds(15))
}

class NoOpHealthSnapshotCache : HealthSnapshotCache {
    override fun getSummaryJson(subscriptionId: Int): String? = null
    override fun putSummaryJson(subscriptionId: Int, json: String, ttl: Duration) = Unit
}

data class LiveTrafficSample(
    val avgMbpsDown: Double?,
    val avgMbpsUp: Double?,
    val collectedAt: Instant?,
    val sampleStatus: String,
    val hostDeviceId: Int?,
)

data class LiveOnuState(
    val sn: String,
    val runState: String?,
    val rxPowerDbm: Double?,
    val observedAt: Instant,
    val updateKind: String = "snapshot",
)

interface LiveTelemetryPort {
    fun traffic(subscriptionId: Int): LiveTrafficSample?
    fun putTraffic(subscriptionId: Int, sample: LiveTrafficSample)
    fun onu(subscriptionId: Int): LiveOnuState?
    fun putOnu(subscriptionId: Int, state: LiveOnuState)
}

class NoOpLiveTelemetry : LiveTelemetryPort {
    override fun traffic(subscriptionId: Int): LiveTrafficSample? = null
    override fun putTraffic(subscriptionId: Int, sample: LiveTrafficSample) = Unit
    override fun onu(subscriptionId: Int): LiveOnuState? = null
    override fun putOnu(subscriptionId: Int, state: LiveOnuState) = Unit
}
