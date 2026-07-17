package com.dscorp.wispadmin.oltgateway.service

data class SyncResult(
    val inserted: Int = 0,
    val updated: Int = 0,
    val softDeleted: Int = 0,
    val unchanged: Int = 0,
    val durationMs: Long = 0,
    val skippedReason: String? = null,
    val error: String? = null
)
