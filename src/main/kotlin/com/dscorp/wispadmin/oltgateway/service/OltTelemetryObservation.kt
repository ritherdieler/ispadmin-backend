package com.dscorp.wispadmin.oltgateway.service

import java.time.Instant

/** Immutable poll result, independent of the mutable inventory/current-state entities. */
data class OltOpticalObservation(val oltId: Long, val observedAt: Instant, val rows: List<OltSignalPollService.OpticalRow>)
data class OltStateObservation(val sn: String, val state: String?, val cause: String?, val observedAt: Instant)

data class OltOpticalFailure(val oltId: Long, val observedAt: Instant, val reason: String)
