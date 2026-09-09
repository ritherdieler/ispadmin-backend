package com.dscorp.wispadmin.oltgateway.service

data class SignalPollResult(
    val slotsPolled: Int = 0,
    val portsPolled: Int = 0,
    val portsFailed: Int = 0,
    val onusUpdated: Int = 0,
    val polledAtRefreshed: Int = 0,
    val incompleteDiscarded: Int = 0,
    val unchangedSkipped: Int = 0,
    val unmatchedRows: Int = 0,
    val rowsMatched: Int = 0,
    val durationMs: Long = 0,
    val skippedReason: String? = null,
    val error: String? = null,
    val localQueueDepth: Int = 0,
    val localBusyJobType: String? = null,
)

data class OpticalApplyStats(
    val onusUpdated: Int = 0,
    val polledAtRefreshed: Int = 0,
    val incompleteDiscarded: Int = 0,
    val unchangedSkipped: Int = 0,
    val unmatchedRows: Int = 0,
    val rowsMatched: Int = 0,
) {
    fun plus(other: OpticalApplyStats): OpticalApplyStats = OpticalApplyStats(
        onusUpdated = onusUpdated + other.onusUpdated,
        polledAtRefreshed = polledAtRefreshed + other.polledAtRefreshed,
        incompleteDiscarded = incompleteDiscarded + other.incompleteDiscarded,
        unchangedSkipped = unchangedSkipped + other.unchangedSkipped,
        unmatchedRows = unmatchedRows + other.unmatchedRows,
        rowsMatched = rowsMatched + other.rowsMatched,
    )
}
