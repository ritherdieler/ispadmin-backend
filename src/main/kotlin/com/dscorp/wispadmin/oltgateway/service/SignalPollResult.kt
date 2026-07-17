package com.dscorp.wispadmin.oltgateway.service

data class SignalPollResult(
    val slotsPolled: Int = 0,
    val portsPolled: Int = 0,
    val onusUpdated: Int = 0,
    val durationMs: Long = 0,
    val skippedReason: String? = null,
    val error: String? = null
)
