package com.dscorp.wispadmin.servicehealth.service

import java.time.Instant

object AcsWifiRefreshPlanner {
    const val STALE_PARAMS = "PARAMETERS_NOT_REFRESHED_FOR_INFORM"

    fun shouldEnqueue(errorReason: String?, lastRequestAt: Instant?, now: Instant, cooldownSeconds: Long): Boolean {
        if (errorReason != STALE_PARAMS) return false
        if (lastRequestAt == null) return true
        return !lastRequestAt.isAfter(now.minusSeconds(cooldownSeconds.coerceAtLeast(0)))
    }

    fun cursorKey(deviceId: String) = "acs-gpv:$deviceId"
    fun stationCursorKey(deviceId: String) = "acs-gpv-sta:$deviceId"

    fun shouldEnqueueStations(expected: Int, parsedStations: Int, lastRequestAt: Instant?, now: Instant, cooldownSeconds: Long): Boolean {
        if (expected <= 0 || parsedStations >= expected) return false
        if (lastRequestAt == null) return true
        return !lastRequestAt.isAfter(now.minusSeconds(cooldownSeconds.coerceAtLeast(0)))
    }
}
