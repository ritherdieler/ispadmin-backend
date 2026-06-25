package com.dscorp.wispadmin.wispadmin.response

data class ChallengeStartResponse(
    val challengeId: String,
    val expiresInMs: Long,
    val enabled: Boolean
)
